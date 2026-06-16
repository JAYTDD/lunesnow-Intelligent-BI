# WebSocket 实时通知系统 - 架构详解

## 一、整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              用户浏览器 (Chrome)                                 │
│                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                          Vue 3 前端应用                                   │  │
│  │                                                                           │  │
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────┐  │  │
│  │  │  BasicLayout    │    │   GlobalSider   │    │   ChartDetailPage   │  │  │
│  │  │                 │    │                 │    │                     │  │  │
│  │  │  useWebSocket() │───▶│  显示连接状态    │    │  显示图表详情        │  │  │
│  │  │       │         │    │  ● 实时连接中    │    │  导出/编辑           │  │  │
│  │  │       │         │    │                 │    │                     │  │  │
│  │  └───────┼─────────┘    └─────────────────┘    └─────────────────────┘  │  │
│  │          │                                                               │  │
│  │          │ WebSocket 连接                                                │  │
│  │          │ ws://localhost:8088/api/ws/chart?userId=123                   │  │
│  │          │                                                               │  │
│  │          ▼                                                               │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐    │  │
│  │  │                    useWebSocket.ts                              │    │  │
│  │  │                                                                 │    │  │
│  │  │  状态:                                                          │    │  │
│  │  │    - connected: boolean     连接状态                            │    │  │
│  │  │    - messages: Message[]    收到的消息列表                       │    │  │
│  │  │                                                                 │    │  │
│  │  │  方法:                                                          │    │  │
│  │  │    - connect()             建立连接                             │    │  │
│  │  │    - disconnect()          断开连接                             │    │  │
│  │  │    - send(data)            发送消息                             │    │  │
│  │  │                                                                 │    │  │
│  │  │  事件处理:                                                      │    │  │
│  │  │    - onopen    → connected = true, 启动心跳                    │    │  │
│  │  │    - onmessage → 解析 JSON, 显示 ElMessage 通知                │    │  │
│  │  │    - onclose   → connected = false, 自动重连                   │    │  │
│  │  │    - onerror   → 打印错误日志                                  │    │  │
│  │  │                                                                 │    │  │
│  │  │  心跳机制:                                                      │    │  │
│  │  │    - 每 30 秒发送 "ping"                                       │    │  │
│  │  │    - 收到 "pong" 响应                                          │    │  │
│  │  │    - 保持连接活跃，防止超时断开                                 │    │  │
│  │  │                                                                 │    │  │
│  │  │  重连机制:                                                      │    │  │
│  │  │    - 断开后自动重连                                             │    │  │
│  │  │    - 指数退避: 1s → 2s → 4s → 8s → 16s → 30s (最大)           │    │  │
│  │  │    - 最多重试 5 次                                              │    │  │
│  │  │                                                                 │    │  │
│  │  └─────────────────────────────────────────────────────────────────┘    │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         │ HTTP 请求 (REST API)
                                         │ WebSocket 连接
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          Spring Boot 后端 (localhost:8088)                       │
│                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                          WebSocket 层                                     │  │
│  │                                                                           │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │
│  │  │                      WebSocketConfig                                │ │  │
│  │  │                                                                     │ │  │
│  │  │  @Configuration                                                     │ │  │
│  │  │  @EnableWebSocket                                                   │ │  │
│  │  │                                                                     │ │  │
│  │  │  registerWebSocketHandlers()                                        │ │  │
│  │  │    - 端点: /ws/chart                                                │ │  │
│  │  │    - 处理器: ChartWebSocketHandler                                  │ │  │
│  │  │    - 允许跨域: setAllowedOrigins("*")                               │ │  │
│  │  │                                                                     │ │  │
│  │  └─────────────────────────────────────────────────────────────────────┘ │  │
│  │                              │                                            │  │
│  │                              ▼                                            │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │
│  │  │                    ChartWebSocketHandler                            │ │  │
│  │  │                                                                     │ │  │
│  │  │  数据存储:                                                          │ │  │
│  │  │  ┌───────────────────────────────────────────────────────────────┐ │ │  │
│  │  │  │  USER_SESSIONS: ConcurrentHashMap<Long, WebSocketSession>    │ │ │  │
│  │  │  │                                                              │ │ │  │
│  │  │  │  Key: userId (用户ID)                                        │ │ │  │
│  │  │  │  Value: WebSocketSession (WebSocket 会话)                    │ │ │  │
│  │  │  │                                                              │ │ │  │
│  │  │  │  示例:                                                       │ │ │  │
│  │  │  │  {                                                           │ │ │  │
│  │  │  │    123: WebSocketSession@1a2b3c,  // 用户123的连接           │ │ │  │
│  │  │  │    456: WebSocketSession@4d5e6f,  // 用户456的连接           │ │ │  │
│  │  │  │    789: WebSocketSession@7g8h9i   // 用户789的连接           │ │ │  │
│  │  │  │  }                                                           │ │ │  │
│  │  │  └───────────────────────────────────────────────────────────────┘ │ │  │
│  │  │                                                                     │ │  │
│  │  │  ┌───────────────────────────────────────────────────────────────┐ │ │  │
│  │  │  │  SESSION_USER_MAP: ConcurrentHashMap<String, Long>           │ │ │  │
│  │  │  │                                                              │ │ │  │
│  │  │  │  Key: sessionId (会话ID)                                     │ │ │  │
│  │  │  │  Value: userId (用户ID)                                      │ │ │  │
│  │  │  │                                                              │ │ │  │
│  │  │  │  用途: 连接关闭时，根据 sessionId 找到 userId，清理映射       │ │ │  │
│  │  │  └───────────────────────────────────────────────────────────────┘ │ │  │
│  │  │                                                                     │ │  │
│  │  │  生命周期方法:                                                      │ │  │
│  │  │  ┌───────────────────────────────────────────────────────────────┐ │ │  │
│  │  │  │  afterConnectionEstablished(session)                         │ │ │  │
│  │  │  │    - 从 URL 解析 userId: ?userId=123                         │ │ │  │
│  │  │  │    - 保存映射: USER_SESSIONS.put(123, session)               │ │ │  │
│  │  │  │    - 保存反向映射: SESSION_USER_MAP.put(sessionId, 123)      │ │ │  │
│  │  │  │                                                              │ │ │  │
│  │  │  │  afterConnectionClosed(session, status)                      │ │ │  │
│  │  │  │    - 从 SESSION_USER_MAP 找到 userId                         │ │ │  │
│  │  │  │    - 清理两个映射                                            │ │ │  │
│  │  │  │                                                              │ │ │  │
│  │  │  │  handleTextMessage(session, message)                         │ │ │  │
│  │  │  │    - 如果收到 "ping"，回复 "pong"                            │ │ │  │
│  │  │  └───────────────────────────────────────────────────────────────┘ │ │  │
│  │  │                                                                     │ │  │
│  │  │  推送方法:                                                          │ │  │
│  │  │  ┌───────────────────────────────────────────────────────────────┐ │ │  │
│  │  │  │  sendToUser(userId, message)                                 │ │ │  │
│  │  │  │    - 从 USER_SESSIONS 获取 session                           │ │ │  │
│  │  │  │    - session.sendMessage(new TextMessage(message))           │ │ │  │
│  │  │  │                                                              │ │ │  │
│  │  │  │  notifyChartSuccess(userId, chartId, chartName)              │ │ │  │
│  │  │  │    - 构建 JSON: {"type":"success","chartId":456,...}         │ │ │  │
│  │  │  │    - 调用 sendToUser()                                       │ │ │  │
│  │  │  │                                                              │ │ │  │
│  │  │  │  notifyChartFailure(userId, chartId, chartName, reason)      │ │ │  │
│  │  │  │    - 构建 JSON: {"type":"failure","chartId":456,...}         │ │ │  │
│  │  │  │    - 调用 sendToUser()                                       │ │ │  │
│  │  │  └───────────────────────────────────────────────────────────────┘ │ │  │
│  │  └─────────────────────────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                          业务层                                           │  │
│  │                                                                           │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │
│  │  │                    ChartMessageConsumer                             │ │  │
│  │  │                                                                     │ │  │
│  │  │  @RabbitListener(queues = "chart.queue")                           │ │  │
│  │  │  handleChartTask(message, channel)                                 │ │  │
│  │  │                                                                     │ │  │
│  │  │    │                                                               │ │  │
│  │  │    ├── 调用 DeepSeek AI 生成图表                                    │ │  │
│  │  │    │                                                               │ │  │
│  │  │    ├── 成功:                                                       │ │  │
│  │  │    │   ├── 更新数据库 status = "succeed"                           │ │  │
│  │  │    │   ├── 释放任务槽位 chartTaskLimiter.release()                 │ │  │
│  │  │    │   └── 调用 chartWebSocketHandler.notifyChartSuccess()  ◀─────┼── WebSocket 推送
│  │  │    │                                                               │ │  │
│  │  │    └── 失败:                                                       │ │  │
│  │  │        ├── 更新数据库 status = "failed"                            │ │  │
│  │  │        ├── 释放任务槽位 chartTaskLimiter.release()                 │ │  │
│  │  │        ├── 拒绝消息进入死信队列 channel.basicNack()                │ │  │
│  │  │        └── 调用 chartWebSocketHandler.notifyChartFailure() ◀──────┼── WebSocket 推送
│  │  │                                                                     │ │  │
│  │  └─────────────────────────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                          数据层                                           │  │
│  │                                                                           │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │  │
│  │  │    MySQL     │  │    Redis     │  │  RabbitMQ    │                   │  │
│  │  │              │  │              │  │              │                   │  │
│  │  │  chart 表    │  │  Session     │  │  chart.queue │                   │  │
│  │  │  - id        │  │  任务计数    │  │  死信队列    │                   │  │
│  │  │  - name      │  │  限流状态    │  │              │                   │  │
│  │  │  - status    │  │              │  │              │                   │  │
│  │  │  - userId    │  │              │  │              │                   │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘                   │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、连接流程详解

### 2.1 前端建立连接

```
┌─────────────────────────────────────────────────────────────────┐
│  1. 用户登录成功                                                 │
│     │                                                           │
│     ▼                                                           │
│  2. BasicLayout.vue 加载                                        │
│     │                                                           │
│     ▼                                                           │
│  3. 执行 useWebSocket()                                         │
│     │                                                           │
│     ├── 获取 userId = loginUserStore.loginUser.id               │
│     │                                                           │
│     ├── 构建 URL = "ws://localhost:8088/api/ws/chart?userId=123"│
│     │                                                           │
│     ▼                                                           │
│  4. new WebSocket(wsUrl)                                        │
│     │                                                           │
│     │  浏览器发起 WebSocket 握手请求:                            │
│     │  GET /api/ws/chart?userId=123 HTTP/1.1                    │
│     │  Upgrade: websocket                                       │
│     │  Connection: Upgrade                                      │
│     │  Sec-WebSocket-Key: xxxxx                                 │
│     │                                                           │
│     ▼                                                           │
│  5. 后端接收请求                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 后端处理连接

```
┌─────────────────────────────────────────────────────────────────┐
│  1. WebSocketConfig 匹配端点 "/ws/chart"                        │
│     │                                                           │
│     ▼                                                           │
│  2. ChartWebSocketHandler.afterConnectionEstablished(session)   │
│     │                                                           │
│     ├── 解析 URL 参数: session.getUri().getQuery()              │
│     │   返回: "userId=123"                                      │
│     │                                                           │
│     ├── parseUserId("userId=123")                               │
│     │   返回: 123                                               │
│     │                                                           │
│     ├── 保存映射:                                                │
│     │   USER_SESSIONS.put(123, session)                         │
│     │   SESSION_USER_MAP.put("session-id-abc", 123)             │
│     │                                                           │
│     ▼                                                           │
│  3. 连接建立成功                                                 │
│     - 前端 ws.onopen 触发                                       │
│     - connected = true                                          │
│     - 侧边栏显示 "● 实时连接中"                                  │
│     - 启动心跳定时器 (每30秒)                                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、数据存储详解

### 3.1 USER_SESSIONS 存储结构

```java
// ConcurrentHashMap 线程安全的 HashMap
private static final Map<Long, WebSocketSession> USER_SESSIONS = new ConcurrentHashMap<>();

// 内存结构示意:
{
    123L: WebSocketSession {
        id: "session-001",
        uri: "ws://localhost:8088/api/ws/chart?userId=123",
        open: true,
        // ... 其他会话属性
    },
    456L: WebSocketSession {
        id: "session-002",
        uri: "ws://localhost:8088/api/ws/chart?userId=456",
        open: true,
    }
}
```

**为什么用 ConcurrentHashMap？**
- 多个线程可能同时访问（多个用户同时连接）
- 线程安全，不需要额外加锁

### 3.2 SESSION_USER_MAP 存储结构

```java
// 反向映射：用于连接关闭时清理
private static final Map<String, Long> SESSION_USER_MAP = new ConcurrentHashMap<>();

// 内存结构示意:
{
    "session-001": 123L,  // sessionId → userId
    "session-002": 456L,
}
```

**为什么需要反向映射？**
- 连接关闭时，只能拿到 `session.getId()`
- 需要找到对应的 `userId` 才能清理 `USER_SESSIONS`

---

## 四、消息推送详解

### 4.1 成功通知流程

```
┌─────────────────────────────────────────────────────────────────┐
│  ChartMessageConsumer.handleChartTask()                         │
│     │                                                           │
│     ├── AI 生成成功                                              │
│     │                                                           │
│     ├── 更新数据库:                                              │
│     │   UPDATE chart SET status='succeed' WHERE id=456          │
│     │                                                           │
│     ├── 释放任务槽位:                                            │
│     │   chartTaskLimiter.release(userId=123)                    │
│     │                                                           │
│     ▼                                                           │
│  chartWebSocketHandler.notifyChartSuccess(                      │
│      userId = 123,        // 谁的图表                           │
│      chartId = 456,       // 哪个图表                           │
│      chartName = "销售趋势"  // 图表名称                         │
│  )                                                              │
│     │                                                           │
│     ▼                                                           │
│  构建 JSON 消息:                                                 │
│  {                                                              │
│      "type": "success",                                         │
│      "chartId": 456,                                            │
│      "chartName": "销售趋势",                                   │
│      "message": "图表生成成功"                                   │
│  }                                                              │
│     │                                                           │
│     ▼                                                           │
│  sendToUser(123, jsonString)                                    │
│     │                                                           │
│     ├── 从 USER_SESSIONS 获取 session:                          │
│     │   session = USER_SESSIONS.get(123)                        │
│     │                                                           │
│     ├── 检查连接是否打开:                                        │
│     │   if (session != null && session.isOpen())                │
│     │                                                           │
│     ▼                                                           │
│  session.sendMessage(new TextMessage(jsonString))               │
│     │                                                           │
│     │  通过 WebSocket 协议发送到浏览器                            │
│     │                                                           │
│     ▼                                                           │
│  前端 ws.onmessage 触发                                         │
│     │                                                           │
│     ├── event.data = '{"type":"success",...}'                   │
│     │                                                           │
│     ├── JSON.parse(event.data)                                  │
│     │                                                           │
│     ├── data.type === 'success'                                 │
│     │                                                           │
│     ▼                                                           │
│  ElMessage.success('图表"销售趋势"生成成功')                     │
│     │                                                           │
│     ▼                                                           │
│  用户看到右上角绿色提示弹窗                                       │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 失败通知流程

```
┌─────────────────────────────────────────────────────────────────┐
│  ChartMessageConsumer.handleChartTask()                         │
│     │                                                           │
│     ├── AI 生成失败 (异常)                                       │
│     │                                                           │
│     ├── 更新数据库:                                              │
│     │   UPDATE chart SET status='failed',                       │
│     │          exec_message='错误信息' WHERE id=456              │
│     │                                                           │
│     ├── 释放任务槽位:                                            │
│     │   chartTaskLimiter.release(userId=123)                    │
│     │                                                           │
│     ├── 拒绝消息进入死信队列:                                     │
│     │   channel.basicNack(deliveryTag, false, false)            │
│     │                                                           │
│     ▼                                                           │
│  chartWebSocketHandler.notifyChartFailure(                      │
│      userId = 123,                                              │
│      chartId = 456,                                             │
│      chartName = "销售趋势",                                    │
│      reason = "AI 未生成有效的图表配置"                          │
│  )                                                              │
│     │                                                           │
│     ▼                                                           │
│  构建 JSON 消息:                                                 │
│  {                                                              │
│      "type": "failure",                                         │
│      "chartId": 456,                                            │
│      "chartName": "销售趋势",                                   │
│      "message": "图表生成失败: AI 未生成有效的图表配置"           │
│  }                                                              │
│     │                                                           │
│     ▼                                                           │
│  sendToUser(123, jsonString) → session.sendMessage()            │
│     │                                                           │
│     ▼                                                           │
│  前端 ElMessage.error('图表"销售趋势"生成失败')                  │
│     │                                                           │
│     ▼                                                           │
│  用户看到右上角红色提示弹窗                                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 五、心跳机制详解

```
┌─────────────────────────────────────────────────────────────────┐
│                        心跳流程                                  │
│                                                                 │
│  前端                              后端                         │
│    │                                 │                          │
│    │  startHeartbeat()               │                          │
│    │  setInterval(30000)             │                          │
│    │                                 │                          │
│    │──────── 每30秒 ───────────────▶│                          │
│    │         ws.send('ping')         │                          │
│    │                                 │                          │
│    │                                 │ handleTextMessage()      │
│    │                                 │ if (message == 'ping')   │
│    │                                 │   session.sendMessage    │
│    │                                 │     ('pong')             │
│    │                                 │                          │
│    │◀─────── pong ──────────────────│                          │
│    │                                 │                          │
│    │  onmessage:                     │                          │
│    │  if (event.data === 'pong')     │                          │
│    │    return; // 忽略              │                          │
│    │                                 │                          │
│    │  ... 30秒后再次 ...             │                          │
│    │                                 │                          │
└─────────────────────────────────────────────────────────────────┘

心跳目的:
1. 保持连接活跃，防止代理/防火墙超时断开
2. 检测连接是否正常
3. 及时发现断线并重连
```

---

## 六、重连机制详解

```
┌─────────────────────────────────────────────────────────────────┐
│                        重连流程                                  │
│                                                                 │
│  连接断开 (onclose 触发)                                         │
│    │                                                            │
│    ├── connected = false                                        │
│    │                                                            │
│    ├── 检查是否主动关闭:                                         │
│    │   if (event.code !== 1000)  // 1000 = 正常关闭              │
│    │                                                            │
│    ├── 检查重试次数:                                             │
│    │   if (reconnectCount < maxReconnect)  // 最多5次            │
│    │                                                            │
│    ▼                                                            │
│  计算延迟时间 (指数退避):                                         │
│    │                                                            │
│    │  第1次: delay = 1000 * 2^0 = 1000ms (1秒)                  │
│    │  第2次: delay = 1000 * 2^1 = 2000ms (2秒)                  │
│    │  第3次: delay = 1000 * 2^2 = 4000ms (4秒)                  │
│    │  第4次: delay = 1000 * 2^3 = 8000ms (8秒)                  │
│    │  第5次: delay = 1000 * 2^4 = 16000ms (16秒)                │
│    │                                                            │
│    │  最大限制: delay = Math.min(delay, 30000)  // 最大30秒      │
│    │                                                            │
│    ▼                                                            │
│  setTimeout(() => {                                             │
│      reconnectCount++;                                          │
│      connect();  // 重新连接                                    │
│  }, delay)                                                      │
│    │                                                            │
│    ▼                                                            │
│  连接成功 → reconnectCount = 0 (重置)                            │
│  连接失败 → 继续重试                                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 七、关键代码文件

| 文件 | 路径 | 作用 |
|---|---|---|
| WebSocketConfig.java | backend/config/ | 注册 WebSocket 端点 |
| ChartWebSocketHandler.java | backend/websocket/ | 管理连接、推送消息 |
| ChartMessageConsumer.java | backend/mq/ | 业务触发通知 |
| useWebSocket.ts | frontend/composables/ | 前端连接管理 |
| BasicLayout.vue | frontend/layouts/ | 建立连接 |
| GlobalSider.vue | frontend/components/layout/ | 显示连接状态 |
| pom.xml | backend/ | 添加 websocket 依赖 |
