## 设计动机与架构定位

在前端实时通信方案的选择上，WebSocket 相比轮询（Polling）提供了真正的"服务器推送"能力——当后端 AI 图表生成任务完成时，无需前端反复发起 HTTP 请求查询状态，服务端可直接将结果推送到目标用户。本项目采用**双轨并行的实时通知策略**：`useWebSocket` 负责即时推送（图表生成完成/失败时弹出通知），`usePolling` 负责兜底状态同步（在列表页轮询图表状态变更）。这种设计既保证了通知的即时性，又通过轮询弥补了 WebSocket 可能因网络波动而丢失消息的缺陷。

**架构层级**：`BasicLayout`（应用壳层）负责建立连接 → `GlobalSider`（侧边栏）展示连接状态指示器 → 消息通过 `ElMessage` 通知组件浮层展示。WebSocket 连接的生命周期与用户会话绑定：登录后自动建立，登出/组件卸载时自动断开。

Sources: [lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts](lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts#L1-L165), [WEBSOCKET_ARCHITECTURE.md](WEBSOCKET_ARCHITECTURE.md#L1-L491)

## 连接管理：自动构建 URL 与身份绑定

### URL 构建策略

前端 WebSocket 连接地址采用**环境感知自动构建**策略，无需人工配置：

```typescript
const wsUrl = url || (() => {
  const isDev = import.meta.env.DEV
  const backendHost = isDev ? 'localhost:8088' : window.location.host
  const protocol = isDev ? 'ws:' : window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${backendHost}/api/ws/chart?userId=${userId}`
})()
```

核心逻辑如下表所示：

| 环境 | Protocol | Host | 示例 URL |
|------|----------|------|----------|
| 开发环境（localhost） | `ws:` | `localhost:8088` | `ws://localhost:8088/api/ws/chart?userId=1` |
| 生产环境（HTTPS） | `wss:` | `window.location.host` | `wss://lunesnow.com/api/ws/chart?userId=1` |
| 生产环境（HTTP） | `ws:` | `window.location.host` | `ws://lunesnow.com/api/ws/chart?userId=1` |

关键设计点：**用户 ID 通过 URL Query 参数传递**，而非在 WebSocket 握手 Header 中携带 Token。这意味着后端 `ChartWebSocketHandler.afterConnectionEstablished()` 从 `session.getUri().getQuery()` 中解析 `userId` 参数，若解析失败或用户未登录（`userId <= 0`），服务端直接以 `CloseStatus.POLICY_VIOLATION` 拒绝连接。这种设计适用于 Session 鉴权场景——前端已通过登录获取了 Session Cookie，WebSocket 握手时 Cookie 会自动携带，但 `userId` 作为业务标识仍需显式传递。

Sources: [lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts](lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts#L17-L29), [lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/websocket/ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/websocket/ChartWebSocketHandler.java#L35-L55)

## 心跳保活机制：阻止连接超时断开

### 为什么需要心跳

WebSocket 连接在长时间无数据交互时，中间网络设备（NAT 网关、负载均衡器、反向代理）可能会主动关闭空闲连接。心跳机制通过定期发送小数据包来维持连接活跃状态，避免因网络设备超时策略导致的意外断开。

### 实现细节

前端 `useWebSocket` 在 `onopen` 事件触发后立即启动心跳定时器：

```typescript
const startHeartbeat = () => {
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send('ping')
    }
  }, 30000)  // 每 30 秒发送一次
}
```

服务端 `ChartWebSocketHandler.handleTextMessage()` 检测到 `"ping"` 消息后，回复 `"pong"`：

```typescript
if ("ping".equals(payload)) {
  session.sendMessage(new TextMessage("pong"));
}
```

前端 `onmessage` 处理函数中，第一条判断即过滤心跳响应：

```typescript
if (event.data === 'pong') return  // 心跳响应忽略，不加入消息列表
```

**设计要点**：
- 心跳间隔固定为 **30 秒**，与多数云负载均衡器的空闲超时阈值（通常为 60 秒）保持安全距离
- 心跳发送前检查 `ws.readyState === WebSocket.OPEN`，避免在连接已关闭时发送无效消息
- `"pong"` 响应不进入 `messages` 响应式数组，避免污染消息列表
- 断开连接时通过 `stopHeartbeat()` 清除定时器，防止内存泄漏

Sources: [lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts](lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts#L88-L99), [lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/websocket/ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/websocket/ChartWebSocketHandler.java#L72-L79)

## 指数退避重连：从激进到保守的容错策略

### 算法设计

WebSocket 连接的 `onclose` 事件处理函数中，实现了经典的**指数退避（Exponential Backoff）**重连策略：

```typescript
ws.onclose = (event) => {
  connected.value = false
  stopHeartbeat()

  // 非主动关闭 → 尝试重连
  if (event.code !== 1000 && reconnectCount < maxReconnect) {
    const delay = Math.min(1000 * Math.pow(2, reconnectCount), 30000)
    reconnectTimer = setTimeout(() => {
      reconnectCount++
      connect()
    }, delay)
  }
}
```

重连延迟的递进序列如下：

| 重试次数 | 退避公式 | 实际延迟 | 累计耗时 |
|----------|----------|----------|----------|
| 0 | `1000 * 2^0` | 1,000 ms | 1s |
| 1 | `1000 * 2^1` | 2,000 ms | 3s |
| 2 | `1000 * 2^2` | 4,000 ms | 7s |
| 3 | `1000 * 2^3` | 8,000 ms | 15s |
| 4 | `1000 * 2^4` | 16,000 ms | 31s |
| 5（已到上限） | — | 停止重试 | — |

### 关键决策点

**1. `event.code !== 1000` 条件**：WebSocket 关闭码 `1000`（CLOSE_NORMAL）表示主动正常关闭。当用户登出或组件卸载调用 `disconnect()` 时，会主动传入 `1000` 码，此时不触发重连。只有非正常关闭（网络中断、服务端重启等）才会启动重连逻辑。

**2. 最大重试上限 `maxReconnect = 5`**：限制重连次数避免无限循环消耗客户端资源。5 次重试覆盖了约 31 秒的时间窗口，足以应对大多数临时性网络波动。

**3. 最小最大值裁剪 `Math.min(..., 30000)`**：防止指数增长失控，最大延迟不超过 30 秒，避免重连间隔过长导致用户等待太久。

**4. 重连计数器重置**：成功连接后 `reconnectCount = 0`，下次断开时重新从 1 秒开始退避。

Sources: [lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts](lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts#L68-L85)

## 消息接收与通知：自动解析与用户提示

### 消息协议定义

前端定义了严格的消息接口类型：

```typescript
interface WebSocketMessage {
  type: 'success' | 'failure' | 'info'     // 消息类型
  chartId?: number                           // 图表 ID
  chartName?: string                         // 图表名称
  message: string                            // 消息内容
}
```

### 消息处理流程

`onmessage` 事件处理函数执行以下步骤：

```
收到消息 → 过滤 "pong" 心跳响应 → JSON.parse 解析 → 
添加至 messages 数组 → 根据 type 弹出 ElMessage 通知
```

通知的差异化配置：

| type | 组件 | 持续时间 | 文案模板 |
|------|------|----------|----------|
| `success` | `ElMessage.success` | 5 秒 | 图表"xxx"生成成功 |
| `failure` | `ElMessage.error` | 8 秒 | 图表"xxx"生成失败 |
| `info` | 仅存入消息列表 | — | 不弹出通知 |

失败通知的持续时间（8 秒）长于成功通知（5 秒），这是因为用户需要更多时间阅读错误信息并决定后续操作。所有消息均追加到 `messages` 响应式数组中，消费端（如 `BasicLayout`）可通过 `messages` 实现自定义的消息中心功能。

Sources: [lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts](lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts#L5-L11), [lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts](lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts#L46-L66)

## 生命周期管理：组件卸载自动清理

### Vue 3 组合式 API 的生命周期绑定

`useWebSocket` 在函数内部直接调用 `onUnmounted` 注册清理函数，这是组合式 API 的经典模式——Hook 自身管理副作用，消费者无需手动清理：

```typescript
// 自动连接
connect()

// 组件卸载时断开
onUnmounted(() => {
  disconnect()
})
```

### 清理三要素

`disconnect()` 方法的职责链：

```typescript
const disconnect = () => {
  // 1. 清除重连定时器
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  // 2. 停止心跳
  stopHeartbeat()
  // 3. 关闭 WebSocket 连接
  if (ws) {
    ws.close(1000, '手动关闭')  // 主动关闭，不会触发重连
    ws = null
  }
  // 4. 重置状态
  connected.value = false
}
```

清理顺序的重要性：**先清除定时器，后关闭连接**。若先关闭 WebSocket 连接，`onclose` 事件会同步触发，此时若 `reconnectTimer` 尚未清除，会误判为异常关闭并启动重连。通过先清理定时器，确保 `onclose` 事件中的重连检测条件 `event.code !== 1000` 虽然满足（`1000` 是主动关闭），但不会有新的 `setTimeout` 在等待执行。

### 在应用中的使用方式

`BasicLayout.vue` 作为应用的壳组件，在 `setup` 中直接调用 `useWebSocket()`：

```vue
<script setup lang="ts">
import { useWebSocket } from '@/composables/useWebSocket'
const { connected: wsConnected } = useWebSocket()
</script>
```

`wsConnected` 响应式引用传递给 `GlobalSider` 组件，侧边栏据此显示连接状态指示器：

```vue
<span class="ws-status" :class="{ 'ws-online': connected }"></span>
{{ connected ? '实时连接中' : '离线' }}
```

状态指示器使用绿色圆点 + 阴影发光效果（`box-shadow: 0 0 8px rgba(16, 185, 129, 0.4)`）直观反映连接健康度。

Sources: [lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts](lunesnow-IntelligentBI-frontend/src/composables/useWebSocket.ts#L103-L115), [lunesnow-IntelligentBI-frontend/src/layouts/BasicLayout.vue](lunesnow-IntelligentBI-frontend/src/layouts/BasicLayout.vue#L1-L68), [lunesnow-IntelligentBI-frontend/src/components/layout/GlobalSider.vue](lunesnow-IntelligentBI-frontend/src/components/layout/GlobalSider.vue#L23-L25)

## 双轨并行：WebSocket 与轮询的协同关系

### 职责分工

本项目同时实现了 WebSocket 推送和轮询两种状态获取机制，它们各自承担不同的角色：

```
┌─────────────────────────────────────────────────────────────────────┐
│                        用户浏览器                                      │
│                                                                     │
│  ┌────────────────────────────────────────┐  ┌───────────────────┐  │
│  │        useWebSocket (即时推送)          │  │  usePolling (兜底)  │  │
│  │                                        │  │                    │  │
│  │  连接位置: BasicLayout（全局壳组件）      │  │  连接位置: MyChartsPage  │  │
│  │  触发方式: 服务端主动推送               │  │  触发方式: 客户端主动查询 │  │
│  │  消息内容: 图表生成成功/失败通知        │  │  查询内容: 图表状态轮询 │  │
│  │  通知形式: ElMessage 弹窗              │  │  通知形式: 列表状态更新 │  │
│  │  可靠性: 连接断开时丢失消息            │  │  可靠性: 有消息丢失风险 │  │
│  └────────────────────────────────────────┘  └───────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**WebSocket 的优势**在于即时性——当 AI 生成任务完成后，`ChartMessageConsumer` 立即调用 `chartWebSocketHandler.notifyChartSuccess()` 推送消息，用户几乎无感知延迟。**轮询的作用**在于兜底——用户切换到"我的图表"页面时，`usePolling` 以 3 秒初始间隔查询所有 `waiting`/`running` 状态的图表，确保即使 WebSocket 连接断开，状态更新也不会遗漏。

Sources: [lunesnow-IntelligentBI-frontend/src/composables/usePolling.ts](lunesnow-IntelligentBI-frontend/src/composables/usePolling.ts#L1-L149), [lunesnow-IntelligentBI-frontend/src/views/MyChartsPage.vue](lunesnow-IntelligentBI-frontend/src/views/MyChartsPage.vue#L234-L260)

## 后端推送链路：从消息队列到 WebSocket

### 完整的推送链路

```
ChartMessageConsumer (消费 RabbitMQ 消息)
    │
    ├── AI 生成成功
    │   ├── 更新数据库 status = "succeed"
    │   ├── 释放 Redis 任务槽位
    │   └── chartWebSocketHandler.notifyChartSuccess(userId, chartId, chartName)
    │       └── sendToUser(userId, message)
    │           ├── USER_SESSIONS.get(userId) → 获取 WebSocketSession
    │           ├── session.isOpen() 检查
    │           └── session.sendMessage(new TextMessage(json))
    │
    └── AI 生成失败
        ├── 更新数据库 status = "failed"
        ├── 释放 Redis 任务槽位
        ├── channel.basicNack() → 消息进入死信队列
        └── chartWebSocketHandler.notifyChartFailure(userId, chartId, chartName, reason)
```

服务端 `ChartWebSocketHandler` 维护了两个 `ConcurrentHashMap` 用于会话管理：

| 数据结构 | 键 | 值 | 用途 |
|----------|-----|------|------|
| `USER_SESSIONS` | `Long userId` | `WebSocketSession` | 按用户 ID 查找会话，用于推送 |
| `SESSION_USER_MAP` | `String sessionId` | `Long userId` | 按会话 ID 反向查找用户，用于清理 |

两个映射的设计确保了 O(1) 时间复杂度的双向查找——推送时通过 `userId` 找 `session`，断开时通过 `sessionId` 找 `userId`。`ConcurrentHashMap` 的选择保证了高并发场景下的线程安全，无需显式加锁。

Sources: [lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/mq/ChartMessageConsumer.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/mq/ChartMessageConsumer.java#L101-L150), [lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/websocket/ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/websocket/ChartWebSocketHandler.java#L22-L30)

## 下一步阅读

WebSocket 客户端封装是前端实时通信的核心组件，与之紧密相关的文档包括：

- [轮询策略优化：指数退避算法与 Page Visibility API 暂停/恢复](22-lun-xun-ce-lue-you-hua-zhi-shu-tui-bi-suan-fa-yu-page-visibility-api-zan-ting-hui-fu) — 了解轮询侧如何与 WebSocket 互补，以及 Page Visibility API 的暂停/恢复机制
- [WebSocket 实时推送：ConcurrentHashMap 会话管理、心跳检测与在线状态](10-websocket-shi-shi-tui-song-concurrenthashmap-hui-hua-guan-li-xin-tiao-jian-ce-yu-zai-xian-zhuang-tai) — 了解服务端 WebSocket 处理的完整实现
- [RabbitMQ 消息队列：可靠投递、手动 ACK 与死信队列重试机制](8-rabbitmq-xiao-xi-dui-lie-ke-kao-tou-di-shou-dong-ack-yu-si-xin-dui-lie-zhong-shi-ji-zhi) — 理解消息如何从 AI 处理到 WebSocket 推送的全链路