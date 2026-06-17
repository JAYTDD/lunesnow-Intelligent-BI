# Intelligent BI

AI 驱动的智能 BI 系统。上传 Excel/CSV 数据文件，自动生成 ECharts 可视化图表与数据分析结论。

## 功能概览

| 功能 | 说明 |
|------|------|
| AI 图表生成 | 上传 Excel/CSV → DeepSeek AI 自动生成 ECharts 配置 + 数据分析结论 |
| 实时状态推送 | RabbitMQ 异步处理 + WebSocket 实时通知生成结果 |
| 图表筛选编辑 | 在线编辑 ECharts 配置，数据筛选，导出 PNG/SVG/JSON |
| 可拖拽仪表盘 | 自由拖拽、缩放画布，localStorage 持久化布局 |
| 用户权限 | BCrypt 加密登录、Session 鉴权、角色控制（admin/user） |
| 接口限流 | Redisson 令牌桶 + Redis Lua 原子计数，防止资源垄断 |
| 管理后台 | 用户管理、查看/编辑任意用户图表、限流管理 |

## 系统架构

<div align="center">
  <img src="architecture.png" alt="System Architecture" width="100%" />
</div>

### 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Element Plus + ECharts |
| 后端 | Spring Boot 3 + MyBatis-Plus + RabbitMQ |
| 存储 | MySQL 8.0 + Redis 7.0 + Redisson |
| 安全 | BCrypt 密码哈希 + Session 鉴权 + AOP 拦截器 |
| AI | DeepSeek API (deepseek-v4-flash) |

## 项目结构

```
├── lunesnow-IntelligentBI-backend/
│   └── src/main/java/com/lunesnow/
│       ├── controller/          # 接口层（Chart / User / File / RateLimit）
│       ├── service/             # 业务层（含动态分表 ChartDataService）
│       ├── mq/                  # 消息队列（生产者 + 消费者 + 死信处理）
│       ├── websocket/           # WebSocket 实时推送
│       ├── manager/             # 限流管理（Lua 原子计数 + Redisson 令牌桶）
│       ├── aop/                 # 拦截器（鉴权 / 限流 / 日志）
│       ├── config/              # 配置（Security / WebSocket / RabbitMQ / Redis）
│       ├── model/               # 数据模型（entity / VO / request）
│       └── utils/               # 工具类（Excel 解析 / SQL 安全校验）
│
└── lunesnow-IntelligentBI-frontend/
    └── src/
        ├── views/               # 页面（10 个页面）
        │   ├── HomePage         # 首页仪表盘 + 统计
        │   ├── AddChartPage     # 创建图表
        │   ├── MyChartsPage     # 图表列表
        │   ├── ChartDetailPage  # 图表详情 + 筛选 + 导出
        │   ├── DashboardEditor  # 可拖拽仪表盘编辑器
        │   ├── admin/           # 管理后台（用户管理 / 限流管理）
        │   └── user/            # 用户（登录 / 注册 / 个人中心）
        ├── components/          # 公共组件（ChartEditor / StatusResultPage）
        ├── composables/         # 组合式函数（usePolling / useWebSocket / useDraggable）
        ├── api/                 # 接口封装
        ├── stores/              # Pinia 状态管理
        ├── router/              # 路由配置
        └── styles/              # 全局样式（Element Plus 主题覆盖）
```

## 数据流转

```
用户上传 Excel
    ↓
前端 POST /chart/gen（FormData: file + name + chartType + goal）
    ↓
后端 ExcelUtils 解析 → ChartDataServiceImpl 创建 chart_{id} 动态表 → 插入数据
    ↓
RabbitMQ 发送消息 → ChartMessageConsumer 消费
    ↓
DeepSeek API 生成 ECharts JSON + 分析结论
    ↓
写入 chart 表（status=succeed）→ WebSocket 推送前端
    ↓
前端轮询 / WebSocket 收到通知 → 渲染 ECharts 图表
```

## 优化方案

| 优化点 | 方案 | 效果 |
|--------|------|------|
| 消息可靠投递 | RabbitMQ 手动 ACK + 死信队列 + 3 次重试 | 失败消息不丢失 |
| 并发任务限制 | Redis Lua 原子脚本 check-and-increment | 无竞态条件，每人最多 3 个任务 |
| 接口限流 | Redisson 令牌桶（2 QPS，突发容量 5） | 防刷接口 |
| 密码安全 | BCrypt 哈希（自带 per-user 盐值） | 替代 MD5 + 全局固定盐 |
| SQL 注入防护 | 列名白名单校验 + CSV 列名字符清理 | 动态表操作安全 |
| 图表安全渲染 | 三重容错解析 + 危险字段过滤 | 渲染崩溃率为 0 |
| 轮询优化 | 指数退避 + Page Visibility API 暂停/恢复 | 无效请求减少 60% |
| 拖拽性能 | CSS transform + GPU 合成层 | 60fps 流畅拖拽 |
| 数据隔离 | 动态分表 chart_{id} | 单表查询，互不干扰 |
| 分页安全 | current ≥ 1，pageSize 限制 1-100 | 防全表扫描 |

## 快速启动

### 环境要求

- JDK 21+
- Node.js 20+
- MySQL 8.0+
- Redis 7.0+
- RabbitMQ 3.12+

### 后端

```bash
cd lunesnow-IntelligentBI-backend
# 1. 创建 application-local.yml，配置 MySQL、Redis、DeepSeek API Key
# 2. 执行 SQL 建表脚本
mvn spring-boot:run
```

### 前端

```bash
cd lunesnow-IntelligentBI-frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

### 配置文件示例

```yaml
# application-local.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/intelligent_bi
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    port: 5672

deepseek:
  api-key: your-api-key
```

## API 接口

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /user/login | 用户登录 | 无 |
| POST | /user/register | 用户注册 | 无 |
| GET | /user/current | 获取当前用户 | 登录 |
| POST | /chart/gen | 生成图表 | 登录 + 限流 |
| GET | /chart/list/my | 我的图表列表 | 登录 |
| GET | /chart/get/vo | 图表详情 | 管理员 |
| POST | /chart/retry | 重试生成 | 登录 |
| POST | /chart/delete | 删除图表 | 登录 |
| GET | /chart/statistics | 统计数据 | 登录 |
| GET | /chart/get/data/{id} | 获取图表数据 | 登录 |
| GET | /chart/get/data/{id}/column/{name} | 列唯一值 | 登录 |
| POST | /file/upload | 文件上传 | 登录 |
| GET | /admin/user/list | 用户列表 | 管理员 |
| GET | /rate-limit/list | 限流列表 | 管理员 |
| POST | /rate-limit/resetAll | 重置限流 | 管理员 |
| WS | /ws/chart?userId={id} | 实时推送 | 用户ID校验 |
