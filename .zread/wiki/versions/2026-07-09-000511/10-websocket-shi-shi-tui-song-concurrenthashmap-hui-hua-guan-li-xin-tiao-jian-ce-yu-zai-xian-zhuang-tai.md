WebSocket 实时推送层是本系统异步图表生成流水线的**最后一公里**——当 DeepSeek AI 完成图表生成（或失败），消息不再由前端轮询获取，而是通过 WebSocket 长连接直接推送到相应用户的浏览器。这一层由三个核心组件构成：**`WebSocketConfig`** 注册端点并配置跨域、**`ChartWebSocketHandler`** 管理用户会话与消息分发、**`useWebSocket`** 前端 Hook 管理连接生命周期与心跳。本文将深入分析这三点如何协同工作。

## 架构定位：为什么在已有轮询策略的基础上还需要 WebSocket？

系统对图表生成状态追踪采用了**双轨策略**：[图表创建页面](18-tu-biao-chuang-jian-ye-mian-biao-dan-xiao-yan-tuo-zhuai-shang-chuan-yu-yi-bu-ren-wu-zhuang-tai-gen-zong) 在提交异步任务后启动轮询（附带指数退避与 Page Visibility API 优化），WebSocket 则在连接建立后提供**即时推送通道**。二者的关系并非替代，而是互补：WebSocket 用于**首屏即时通知**（用户停留在当前页面时，AI 任务完成瞬间弹出提示），轮询用于**跨页面状态同步**（用户从其他页面返回时获取最新状态）。WebSocket 端点注册在 `/api/ws/chart` 路径下，通过 URL 参数 `?userId={id}` 完成身份绑定，而非使用 HTTP Session 或 Token 头——这是一种**轻量级鉴权策略**，依赖前端在连接时传入已登录用户的 ID。后端在 `afterConnectionEstablished` 中解析并校验该参数，无效则直接拒绝连接。

```
┌─────────────────────────────────────────────────────────────────────┐
│                      WebSocket 架构全景                              │
│                                                                     │
│  前端浏览器                           Spring Boot 后端               │
│  ┌─────────────┐                     ┌───────────────────────────┐  │
│  │ BasicLayout │                     │   WebSocketConfig         │  │
│  │  ┌──────────┤  ws://host/api/     │   @EnableWebSocket        │  │
│  │  │useWebSocket│─ws/chart?userId→ │   /ws/chart 端点注册      │  │
│  │  │  Hook     │                     │   setAllowedOrigins()     │  │
│  │  └──────────┤                     └───────────┬───────────────┘  │
│  │       │                                      │                  │
│  │       │ onopen → 启动心跳(30s)                ▼                  │
│  │       │ onmessage→ 解析JSON→ 弹窗        ChartWebSocketHandler  │
│  │       │ onclose → 指数退避重连         ┌──────────────────────┐ │
│  │       │                                │ USER_SESSIONS        │ │
│  │  GlobalSider                           │ userId→WebSocketSession│ │
│  │  ● 实时连接中                      │ SESSION_USER_MAP      │ │
│  └─────────────┘                     │ sessionId→userId      │ │
│                                       │                      │ │
│                                       │ sendToUser()          │ │
│                                       │ notifyChartSuccess()  │ │
│                                       │ notifyChartFailure()  │ │
│                                       │ isUserOnline()        │ │
│                                       └──────────┬───────────┘  │
│                                                  │              │
│                                        ChartMessageConsumer     │
│                                        (RabbitMQ consumer)      │
│                                        ┌──────────────────────┐ │
│                                        │ handleChartTask()    │ │
│                                        │ 成功→notifyChartSuccess│ │
│                                        │ 失败→notifyChartFailure│ │
│                                        └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

Sources: [ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend\src\main\java\com\lunesnow\websocket\ChartWebSocketHandler.java#L1-L162), [WebSocketConfig.java](lunesnow-IntelligentBI-backend\src\main\java\com\lunesnow\config\WebSocketConfig.java#L1-L33)

## ConcurrentHashMap 双映射：会话管理与快速定位

`ChartWebSocketHandler` 内部维护了两张 `ConcurrentHashMap`，分别承担**正向索引**与**反向索引**的角色，这是整个会话管理的核心数据结构。

| 映射表 | Key 类型 | Value 类型 | 作用 | 查询场景 |
|--------|---------|-----------|------|---------|
| `USER_SESSIONS` | `Long` (userId) | `WebSocketSession` | 按用户 ID 定位会话 | 推送消息时：`USER_SESSIONS.get(userId)` |
| `SESSION_USER_MAP` | `String` (sessionId) | `Long` (userId) | 按会话 ID 定位用户 | 连接关闭时：从 `session.getId()` 反查 userId |

**为什么需要两张映射表？** 当 WebSocket 连接关闭事件触发时，`afterConnectionClosed(WebSocketSession session, CloseStatus status)` 方法只能拿到 `session` 对象本身。通过 `session.getId()` 可以获取 Session 的唯一标识（字符串），但没有反向索引就无法知道该 Session 对应哪个用户，也就无法从 `USER_SESSIONS` 中移除该用户的映射，导致**内存泄漏**——用户已断开连接，但 `USER_SESSIONS` 中仍残留其引用。双映射的设计确保了两个方向的查找都能在 O(1) 时间复杂度内完成，且 `afterConnectionClosed` 中两种清理操作在 `ConcurrentHashMap` 的线程安全保证下并发安全。

```java
// 连接建立时的双映射写入
USER_SESSIONS.put(userId, session);
SESSION_USER_MAP.put(session.getId(), userId);

// 连接关闭时的双映射清理
Long userId = SESSION_USER_MAP.remove(session.getId());
if (userId != null) {
    USER_SESSIONS.remove(userId);
}
```

选择 `ConcurrentHashMap` 而非 `synchronizedMap` 或显式加锁的原因在于：WebSocket 服务端需要同时处理**大量并发连接**（每个连接建立/关闭/收发消息都在独立的 Netty 线程中处理），`ConcurrentHashMap` 采用分段锁（Java 8+ 为桶级锁 + CAS）实现高并发读写，读操作完全无锁，写操作仅锁住对应桶，避免了全局锁竞争。

Sources: [ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend\src\main\java\com\lunesnow\websocket\ChartWebSocketHandler.java#L20-L30)

## 连接生命周期：建立、拒绝与关闭

连接建立的入口是 `afterConnectionEstablished(WebSocketSession session)`。该方法的核心职责是**从 URL 查询参数中提取并验证 userId**：

```java
// 从 URL 解析 userId
// 完整URL示例: ws://localhost:8088/api/ws/chart?userId=123
String query = session.getUri() != null ? session.getUri().getQuery() : null;
Long userId = parseUserId(query);
```

`parseUserId` 方法将 query string 按 `&` 分割、按 `=` 拆解，遍历查找 key 为 `userId` 的参数项，再通过 `Long.parseLong()` 转换为数值类型。任何步骤失败（query 为 null、参数缺失、格式错误）均返回 null，此时 handler 会记录警告日志并主动关闭连接（`POLICY_VIOLATION` 状态码）。这种**静默拒绝**机制防止了未授权或无效的连接占用服务端资源。

连接的关闭触发点在 `afterConnectionClosed`，这里执行前述的双映射清理。值得注意的是，`close` 事件也会在服务端主动关闭时触发，因此**清理逻辑必须幂等**——`SESSION_USER_MAP.remove(session.getId())` 无论调用多少次，第一次之后返回 null，第二次不再执行 `USER_SESSIONS.remove`，避免了重复清理。

Sources: [ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend\src\main\java\com\lunesnow\websocket\ChartWebSocketHandler.java#L33-L67)

## 心跳检测：Ping-Pong 协议与 30 秒周期

WebSocket 连接本身并不保证永久存活——中间网络设备（NAT 网关、负载均衡器、反向代理）可能因空闲超时而切断连接。心跳机制的目的在于**保持连接活跃**并**尽早发现断线**。

心跳的实现模式是**客户端驱动型**：前端 `useWebSocket.ts` 在连接建立后启动一个 30 秒间隔的 `setInterval`，每次向服务端发送字符串 `"ping"`。服务端 `handleTextMessage` 方法收到消息后，检查 payload 是否为 `"ping"`，是则回复 `"pong"`：

```java
if ("ping".equals(payload)) {
    session.sendMessage(new TextMessage("pong"));
}
```

前端 `onmessage` 回调中，第一条判断就是过滤心跳响应：`if (event.data === 'pong') return;`，直接忽略不进入消息处理流程。这种**透传+忽略**模式保证了心跳消息对业务逻辑完全透明。

选择 30 秒作为心跳间隔是平衡**实时性**与**资源消耗**的结果：间隔过短（如 5 秒）导致每分钟产生 12 次额外的网络交互，对移动端或弱网环境不友好；间隔过长（如 5 分钟）则断线检测延迟过高。30 秒是 WebSocket 心跳的常见配置，兼容绝大多数代理服务器的空闲超时设置。

Sources: [ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend\src\main\java\com\lunesnow\websocket\ChartWebSocketHandler.java#L69-L79), [useWebSocket.ts](lunesnow-IntelligentBI-frontend\src\composables\useWebSocket.ts#L54-L60)

## 消息推送与在线状态检测

消息推送的入口是 `sendToUser(Long userId, String message)` 方法，这是**整个系统中所有需要向用户推送消息的模块唯一调用点**。其流程为：

1. 从 `USER_SESSIONS` 中获取用户的 `WebSocketSession`
2. 双重校验：`session != null && session.isOpen()`（session 存在且连接未关闭）
3. 通过 `session.sendMessage(new TextMessage(message))` 发送
4. 成功/失败分别记录日志

**消息推送的幂等性问题**：如果用户同时打开多个浏览器标签页（每个标签页建立独立的 WebSocket 连接），则只有一个标签页会收到推送——因为 `USER_SESSIONS` 是 userId→session 的一对一映射，后建立的连接会覆盖前一个。这是该设计的有意简化：对于图表生成通知而言，用户在一个标签页收到通知后通常不会重复操作，因此无需广播到所有标签页。若未来需要多标签页支持，可将 `USER_SESSIONS` 改为 `Map<Long, Set<WebSocketSession>>` 并遍历推送。

基于 `sendToUser` 之上，handler 提供了两个语义化的便捷方法：

- `notifyChartSuccess(userId, chartId, chartName)` — 构建 `{"type":"success","chartId":...,"chartName":"...","message":"图表生成成功"}` JSON
- `notifyChartFailure(userId, chartId, chartName, reason)` — 构建 `{"type":"failure","chartId":...,"chartName":"...","message":"图表生成失败: ..."}` JSON

这两个方法被 `ChartMessageConsumer` 在 AI 图表生成流程的**成功与失败分支末尾**调用，形成完整的异步处理链路闭环。

在线状态检测通过 `isUserOnline(Long userId)` 方法暴露，内部逻辑同样是 `USER_SESSIONS.get(userId)` 后检查 `session.isOpen()`。这一方法目前主要用于管理后台的在线用户统计，也是未来实现"用户在线状态显示"的基础设施。

Sources: [ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend\src\main\java\com\lunesnow\websocket\ChartWebSocketHandler.java#L81-L133), [ChartMessageConsumer.java](lunesnow-IntelligentBI-backend\src\main\java\com\lunesnow\mq\ChartMessageConsumer.java#L120-L140)

## 前端连接管理：自动重连与指数退避

前端 `useWebSocket.ts` 是一个 Vue 3 Composition API 的 Hook，封装了整个 WebSocket 客户端生命周期。其核心设计体现在三个层面：

**URL 构建策略**：根据环境自动切换协议与地址。开发环境使用 `ws://localhost:8088/api/ws/chart`，生产环境根据 `window.location.protocol` 自动选择 `ws:` 或 `wss:`，host 则取自当前域名。这种设计使得**无需手动配置不同环境的 WebSocket 地址**，尤其在 Docker 部署或反向代理场景下自动适配。

**指数退避重连**：连接断开时（`onclose` 事件触发且 `event.code !== 1000`，即非正常关闭），启动重连流程。每次重试的延迟时间按 `Math.min(1000 * 2^retryCount, 30000)` 计算，形成 1s → 2s → 4s → 8s → 16s → 30s 的递增序列，最大 30 秒封顶。最多重试 5 次（`maxReconnect = 5`），超出后不再重连。重连计数器在连接成功时归零，确保断线重连循环可以持续。

**组件生命周期绑定**：在 `onUnmounted` 钩子中调用 `disconnect()` 方法，清理定时器并关闭连接。`disconnect` 使用 `ws.close(1000, '手动关闭')` 发送正常关闭帧（code=1000），因此不会触发重连逻辑——组件卸载时主动断开连接不应该触发重连。

```typescript
// 核心重连逻辑
ws.onclose = (event) => {
    connected.value = false;
    stopHeartbeat();
    if (event.code !== 1000 && reconnectCount < maxReconnect) {
        const delay = Math.min(1000 * Math.pow(2, reconnectCount), 30000);
        reconnectTimer = setTimeout(() => {
            reconnectCount++;
            connect();
        }, delay);
    }
};
```

Sources: [useWebSocket.ts](lunesnow-IntelligentBI-frontend\src\composables\useWebSocket.ts#L1-L165)

## 配置与跨域

`WebSocketConfig` 通过实现 `WebSocketConfigurer` 接口，在 `registerWebSocketHandlers` 中完成端点和 CORS 配置：

```java
registry.addHandler(chartWebSocketHandler, "/ws/chart")
    .setAllowedOrigins("http://localhost:5173", "http://localhost:3000", "https://lunesnow.com");
```

注意此处 `setAllowedOrigins` 不是传统的 HTTP CORS 头配置——WebSocket 握手阶段的 Origin 校验由 Spring 的 `WebSocketHandlerRegistration` 处理。三个允许的来源分别覆盖开发环境（Vite 默认 5173 端口、其他前端框架 3000 端口）和生产环境（正式域名）。若需调试或新增环境，在此处添加即可。

依赖层面，pom.xml 中引入 `spring-boot-starter-websocket`，该 starter 自动配置了 Spring WebSocket 所需的全部基础设施（包括底层的 WebSocket 容器适配，默认使用 Tomcat 的 WebSocket 实现，Spring Boot 3.x 下支持 Jakarta WebSocket 规范）。

Sources: [WebSocketConfig.java](lunesnow-IntelligentBI-backend\src\main\java\com\lunesnow\config\WebSocketConfig.java#L28-L33), [pom.xml](lunesnow-IntelligentBI-backend\pom.xml#L30-L32)

## 总结与阅读指引

WebSocket 模块在本系统中承担了**异步任务结果即时通知**的关键职责，与 RabbitMQ 消息队列、轮询策略共同构成了完整的状态通知体系。其设计遵循了几个明确的权衡原则：

- **内存会话 vs 分布式会话**：当前采用单机内存 `ConcurrentHashMap` 存储会话，未使用 Redis 或分布式方案。这适合单节点部署场景，若后续扩展为多节点集群，需引入 Redis Pub/Sub 或消息中间件广播机制来同步会话状态。
- **一对一推送 vs 广播推送**：每个用户仅持有一个连接，推送仅发往该连接。对于图表生成这类个人化通知场景，无需广播能力。
- **URL 参数鉴权 vs Token 鉴权**：通过 URL query 传递 userId 简单直接，但安全性依赖于 HTTPS/WSS 加密传输。生产环境应确保使用 `wss://` 协议防止中间人窃取参数。

推荐继续阅读的关联页面：[前端 WebSocket 客户端封装](19-websocket-ke-hu-duan-feng-zhuang-zhi-shu-tui-bi-zhong-lian-xin-tiao-bao-huo-yu-zu-jian-xie-zai-qing-li) 详细分析了 `useWebSocket` Hook 的完整实现；[RabbitMQ 消息队列](8-rabbitmq-xiao-xi-dui-lie-ke-kao-tou-di-shou-dong-ack-yu-si-xin-dui-lie-zhong-shi-ji-zhi) 展示了 WebSocket 推送的上游触发逻辑；[轮询策略优化](22-lun-xun-ce-lue-you-hua-zhi-shu-tui-bi-suan-fa-yu-page-visibility-api-zan-ting-hui-fu) 解释了 WebSocket 的互补方案如何工作。