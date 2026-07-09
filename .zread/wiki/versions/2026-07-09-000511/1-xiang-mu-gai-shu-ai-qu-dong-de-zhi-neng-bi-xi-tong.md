## 系统简介

这是一个将**生成式 AI** 与传统 **BI（商业智能）** 相结合的现代化数据可视化平台。核心价值在于：**用户只需上传一个 Excel 或 CSV 数据文件并描述分析目标，系统即可自动完成从数据存储、AI 分析到 ECharts 图表渲染的全链路流程**，无需手动编写任何代码或配置。

传统的 BI 工具（如 Tableau、Power BI）要求用户具备数据建模和图表配置的知识，学习曲线陡峭。本系统通过引入 DeepSeek AI 大模型，将"数据上传→AI 理解数据→生成 ECharts 配置→渲染可视化图表"这一流程完全自动化，让不具备技术背景的业务人员也能在几分钟内获得专业的数据分析图表。同时，系统通过异步消息队列、WebSocket 实时推送、分布式限流等工程手段，保证了高并发场景下的稳定性和资源公平性。

Sources: [README.md](README.md#L1-L8)

## 目标用户画像

本系统主要面向三类用户群体：

| 用户类型 | 典型场景 | 核心需求 |
|----------|----------|----------|
| **业务分析师** | 上传月度销售数据，让 AI 自动生成趋势图 | 零代码操作、快速出图、多图表管理 |
| **企业管理者** | 查看仪表盘中的多张图表，监控核心指标 | 可视化概览、图表拖拽编排、实时数据 |
| **系统管理员** | 管理用户权限、审计图表内容、监控系统限流状态 | 用户管理、图表审计、限流阈值调节 |

系统通过 `user_role` 字段区分普通用户（user）和管理员（admin），管理员可访问管理后台执行用户管理、图表审计和限流监控等操作。前端路由通过 `meta.requiresAdmin` 标记进行权限控制，后端通过 `@AuthCheck(mustRole = "admin")` 注解在 AOP 切面层统一拦截。

Sources: [router/index.ts](lunesnow-IntelligentBI-frontend/src/router/index.ts#L35-L48), [AuthInterceptor.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/aop/AuthInterceptor.java#L28-L68)

## 系统架构全景

系统采用**前后端分离 + 异步消息驱动**的架构模式。前端使用 Vue 3 + TypeScript 构建单页应用，后端基于 Spring Boot 3 提供 RESTful API，通过 RabbitMQ 消息队列解耦耗时任务以提升响应速度，并使用 WebSocket 实现服务端主动推送。

```mermaid
flowchart TB
    subgraph 前端层["前端层 - Vue 3 SPA"]
        A1[Vue Router<br/>页面路由] --> A2[Element Plus<br/>UI 组件库]
        A2 --> A3[ECharts<br/>图表渲染引擎]
        A4[Pinia<br/>状态管理] --> A2
        A5[composables<br/>useWebSocket/usePolling/useDraggable] --> A2
    end

    subgraph 网关层["网关层 - Nginx / Spring Boot"]
        B1[CORS 跨域配置] --> B2[Spring Security<br/>BCrypt + Session 鉴权]
        B2 --> B3[AOP 切面层]
        B3 --> B4[AuthCheck<br/>权限校验]
        B3 --> B5[RateLimit<br/>分布式限流]
        B3 --> B6[LogInterceptor<br/>日志记录]
    end

    subgraph 业务层["业务层 - RESTful API"]
        C1[ChartController<br/>图表生成/管理] --> C2[ChartService<br/>业务逻辑]
        C1 --> C3[ChartDataService<br/>动态分表操作]
        C4[UserController<br/>用户注册/登录] --> C5[UserService<br/>身份验证]
        C6[FileController<br/>文件上传] --> C7[ExcelUtils<br/>CSV/Excel 解析]
        C8[RateLimitController<br/>限流管理]
    end

    subgraph 异步层["异步处理层"]
        D1[ChartMessageProducer<br/>消息生产者] --> D2[RabbitMQ<br/>主队列 + 死信队列]
        D2 --> D3[ChartMessageConsumer<br/>消息消费者]
        D3 --> D4[DeepSeekUtils<br/>AI API 调用]
        D3 --> D5[ChartWebSocketHandler<br/>结果推送]
    end

    subgraph 数据层["数据存储层"]
        E1[(MySQL 8.0<br/>chart 主表 + chart_{id} 动态表)]
        E2[(Redis 7.0<br/>Session / 限流 / 任务计数)]
        E3[(RabbitMQ<br/>消息持久化)]
    end

    A1 -- HTTP/REST --> B1
    B4 --> C1
    B5 --> C1
    C1 -- 异步提交 --> D1
    D3 -- 写入/更新 --> E1
    D3 -- 释放槽位 --> E2
    C3 -- 动态建表 --> E1
    B5 -- 令牌桶 --> E2
    D5 -- WebSocket 推送 --> A5
    A5 -- 指数退避轮询 --> C1
```

上图中，箭头方向代表数据流或控制流的走向。红色虚线框标注了系统中的四个核心分层：前端层处理用户交互，网关层负责安全控制，业务层处理业务逻辑和 API 路由，异步层承载耗时的 AI 图表生成任务。这种分层的核心设计思想是：**将"上传文件"这个轻量操作与"AI 生成图表"这个重量计算通过消息队列解耦**，用户上传文件后立即获得响应，无需等待 AI 处理完成。

Sources: [MainApplication.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/MainApplication.java#L1-L25), [application.yml](lunesnow-IntelligentBI-backend/src/main/resources/application.yml#L1-L92)

## 核心技术栈

下表列出系统采用的每一层技术选型及其版本和用途：

| 层级 | 技术 | 版本 | 核心用途 |
|------|------|------|----------|
| **前端框架** | Vue 3 + TypeScript | ^3.5.32 + ~6.0.0 | 基于 Composition API 构建响应式 SPA，TypeScript 提供类型安全 |
| **UI 组件库** | Element Plus | ^2.14.1 | 提供表单、表格、对话框、上传等成熟的企业级组件 |
| **图表引擎** | ECharts | ^6.1.0 | 渲染 AI 生成的 JSON 配置为交互式图表，支持导出 PNG/SVG |
| **状态管理** | Pinia | ^3.0.4 | 管理全局用户登录状态和会话信息 |
| **构建工具** | Vite | ^8.0.8 | 开发服务器热更新（HMR），生产构建使用 Rollup |
| **后端框架** | Spring Boot 3 | 3.3.0 | 提供 RESTful API、依赖注入、自动配置 |
| **ORM 框架** | MyBatis-Plus | 3.5.15 | 简化 CRUD 操作、分页查询、逻辑删除 |
| **消息队列** | RabbitMQ | AMQP | 异步解耦图表生成任务，死信队列处理失败重试 |
| **缓存/存储** | Redis 7.0 + Redisson | 7.0 | 分布式 Session、Lua 原子计数、令牌桶限流 |
| **数据库** | MySQL 8.0 | 8.0+ | 存储用户、图表元信息、动态建表存储上传数据 |
| **安全框架** | Spring Security + BCrypt | 6.x | 密码加密（自带 salt）、Session 鉴权、CSRF 关闭 |
| **AI 模型** | DeepSeek API | deepseek-v4-flash | 接收 CSV 数据和分析目标，返回 ECharts JSON 配置 |
| **AOP 切面** | Spring AOP | 6.x | 通过 @AuthCheck 和 @RateLimit 注解实现声明式安全控制 |

前端与后端的通信方式有三种：**RESTful API**（标准 CRUD 操作，使用 Axios 封装）、**WebSocket**（图表生成完成后服务端主动推送通知）、**轮询**（作为 WebSocket 的补充方案，使用指数退避算法优化）。

Sources: [pom.xml](lunesnow-IntelligentBI-backend/pom.xml#L1-L60), [package.json](lunesnow-IntelligentBI-frontend/package.json#L1-L53), [application.yml](lunesnow-IntelligentBI-backend/src/main/resources/application.yml#L1-L92)

## 核心功能矩阵

| 功能模块 | 描述 | 关键实现 | 文件位置 |
|----------|------|----------|----------|
| **AI 图表生成** | 上传 CSV/Excel，AI 自动输出 ECharts 配置 + 分析结论 | DeepSeek API + prompt 工程，输出解析为 JSON | [ChartMessageConsumer.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/mq/ChartMessageConsumer.java#L99-L150) |
| **异步处理** | 任务通过 RabbitMQ 异步执行，前端立即获得任务ID | 手动 ACK + 死信队列 + 3 次重试 | [RabbitConfig.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/config/RabbitConfig.java#L1-L128) |
| **实时推送** | WebSocket 推送生成结果，无需前端反复刷新 | ConcurrentHashMap 会话管理，心跳检测 pong | [ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/websocket/ChartWebSocketHandler.java#L1-L162) |
| **动态分表** | 每次上传自动创建 `chart_{id}` 表，数据物理隔离 | JdbcTemplate 动态建表，列名白名单校验 | [ChartDataServiceImpl.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/service/impl/ChartDataServiceImpl.java#L1-L100) |
| **并发控制** | 每用户最多 3 个并发任务，防止资源垄断 | Redis Lua 原子脚本 check-and-increment | [ChartTaskLimiter.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/manager/ChartTaskLimiter.java#L1-L160) |
| **分布式限流** | 接口级限流，2 QPS + 突发容量 5 | Redisson RRateLimiter 令牌桶 | [RedissonRateLimiter.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/manager/RedissonRateLimiter.java#L1-L184) |
| **在线编辑器** | 可编辑 AI 生成的 ECharts 配置，实时预览 | JSON 安全解析 + 危险字段过滤（__proto__ 等） | [chartValidator.ts](lunesnow-IntelligentBI-frontend/src/utils/chartValidator.ts#L1-L167) |
| **拖拽仪表盘** | 自由拖拽图表卡片，无限画布，布局持久化 | CSS transform GPU 加速 + localStorage 存储 | [useDraggable.ts](lunesnow-IntelligentBI-frontend/src/composables/useDraggable.ts#L1-L140) |
| **用户管理** | 注册、登录、角色控制、Session 鉴权 | BCrypt 密码哈希 + Redis 存储 Session | [SecurityConfig.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/config/SecurityConfig.java#L1-L39) |
| **管理后台** | 用户管理、图表审计、限流监控 | 4 个管理页面，admin 角色专属路由 | [router/index.ts](lunesnow-IntelligentBI-frontend/src/router/index.ts#L35-L48) |

## 完整数据流水线

从用户上传文件到最终看到渲染图表，数据经历了六个阶段。下面通过一个实际例子说明整个过程：

```
用户上传"月度销售数据.xlsx"
    │
    ▼ 阶段一：文件校验与解析
    ① Controller 接收 FormData（file + name + chartType + goal）
    ② ExcelUtils.excelToCsv() 使用 EasyExcel 解析为 CSV 字符串
    ③ 上传文件存储到本地 / 云存储
    │
    ▼ 阶段二：动态建表与数据写入
    ④ ChartDataServiceImpl.createTableFromCsv()
        解析 CSV 第一行获取列名（白名单校验）
        执行 CREATE TABLE chart_{id} (...) 动态建表
        逐行 INSERT 数据到 chart_{id} 表
    │
    ▼ 阶段三：并发控制与异步提交
    ⑤ ChartTaskLimiter.tryAcquire(userId) Redis Lua 检查并发槽位
        若超过 3 个任务，返回"操作过于频繁，请稍后再试"
        若通过，chart 表 status = "waiting"
    ⑥ ChartMessageProducer.sendChartTask(chartId)
        RabbitMQ 消息持久化 + 手动 ACK 模式
    │
    ▼ 阶段四：AI 分析与图表生成
    ⑦ ChartMessageConsumer 消费消息
        status = "running"
        查询 chart_{id} 表获取数据
        构造 prompt → 调用 DeepSeek API
        解析返回的 ECharts JSON + 分析结论
    │
    ▼ 阶段五：结果存储与推送
    ⑧ 更新 chart 表：
        genChart = ECharts JSON 配置
        genResult = 分析结论文本
        status = "succeed"
    ⑨ ChartWebSocketHandler.sendToUser() 推送成功通知
        ChartTaskLimiter.release(userId) 释放并发槽位
    │
    ▼ 阶段六：前端渲染
    ⑩ useWebSocket Hook 收到 {type:"success", chartId, chartName}
        触发轮询或直接跳转至图表详情页
        调用 ECharts setOption(genChart) 渲染图表
        可选：使用 chartValidator.ts 进行安全渲染
```

Source: [ChartController.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/controller/ChartController.java#L210-L280), [ChartMessageConsumer.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/mq/ChartMessageConsumer.java#L1-L100), [ChartWebSocketHandler.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/websocket/ChartWebSocketHandler.java#L100-L130)

## 项目目录结构

```
├── lunesnow-IntelligentBI-backend          # 后端 Spring Boot 3 项目
│   ├── src/main/java/com/lunesnow/
│   │   ├── controller/                     # 4 个控制器（RESTful API 入口）
│   │   │   ├── ChartController.java        # 图表 CRUD + 生成 + 数据查询
│   │   │   ├── UserController.java         # 登录 / 注册 / 个人信息
│   │   │   ├── FileController.java         # 文件上传
│   │   │   └── RateLimitController.java    # 限流管理（管理员）
│   │   ├── service/impl/                   # 业务逻辑实现
│   │   │   ├── ChartServiceImpl.java       # 图表业务 + 分页 + 统计
│   │   │   ├── ChartDataServiceImpl.java   # 动态建表 + 数据导入/查询
│   │   │   └── UserServiceImpl.java        # 用户注册/登录/鉴权
│   │   ├── mq/                             # 消息队列
│   │   │   ├── ChartMessageProducer.java   # 生产者（发送任务）
│   │   │   └── ChartMessageConsumer.java   # 消费者（AI 生成 + 推送结果）
│   │   ├── websocket/                      # WebSocket 实时推送
│   │   │   └── ChartWebSocketHandler.java  # 会话管理 + 心跳 + 消息推送
│   │   ├── manager/                        # 限流管理
│   │   │   ├── ChartTaskLimiter.java       # Redis Lua 并发任务限制
│   │   │   └── RedissonRateLimiter.java    # 令牌桶分布式限流
│   │   ├── aop/                            # AOP 切面
│   │   │   ├── AuthInterceptor.java        # @AuthCheck 权限校验
│   │   │   ├── RateLimitAspect.java        # @RateLimit 限流拦截
│   │   │   └── LogInterceptor.java        # 请求日志记录
│   │   ├── config/                         # 配置类（9 个）
│   │   ├── annotation/                     # 自定义注解（AuthCheck, RateLimit）
│   │   ├── model/                          # 实体 / VO / DTO / 枚举
│   │   └── utils/                          # ExcelUtils 等工具类
│   ├── sql/                                # SQL 建表脚本
│   └── pom.xml                             # Maven 依赖管理
│
├── lunesnow-IntelligentBI-frontend         # 前端 Vue 3 + TypeScript 项目
│   ├── src/
│   │   ├── views/                          # 10 个视图页面
│   │   │   ├── HomePage.vue                # 首页：图表统计概览
│   │   │   ├── AddChartPage.vue            # 图表创建（文件上传 + 表单）
│   │   │   ├── MyChartsPage.vue            # 我的图表列表
│   │   │   ├── ChartDetailPage.vue         # 图表详情（筛选 + 编辑 + 导出）
│   │   │   ├── DashboardEditor.vue         # 可拖拽仪表盘
│   │   │   └── admin/                      # 管理后台（3 个页面）
│   │   ├── composables/                    # 组合式函数
│   │   │   ├── useWebSocket.ts             # WebSocket 客户端封装
│   │   │   ├── usePolling.ts               # 指数退避轮询
│   │   │   └── useDraggable.ts             # 拖拽交互
│   │   ├── utils/                          # 工具函数
│   │   │   └── chartValidator.ts           # ECharts 安全解析与校验
│   │   ├── router/index.ts                 # 路由定义（12 条路由）
│   │   ├── stores/                         # Pinia 状态管理
│   │   ├── api/                            # Axios API 封装
│   │   └── styles/                         # 全局样式
│   ├── package.json                        # 前端依赖
│   └── vite.config.ts                      # Vite 构建配置
```

Sources: [README.md](README.md#L28-L51), [README.md](README.md#L52-L74)

## 优化设计一览

系统在可靠性、安全性、性能和用户体验四个维度进行了体系化的优化设计：

| 维度 | 优化项 | 技术方案 | 效果指标 |
|------|--------|----------|----------|
| **可靠性** | 消息可靠投递 | RabbitMQ 手动 ACK + 死信队列 + 3 次重试 | 失败消息不丢失，自动重试 |
| **可靠性** | 并发任务限制 | Redis Lua 原子脚本 check-and-increment | 无竞态条件，每人最多 3 个任务 |
| **安全性** | 密码存储 | BCrypt 哈希（每用户独立盐值） | 替代 MD5 + 全局固定盐 |
| **安全性** | SQL 注入防护 | 列名白名单校验 + CSV 列名字符清理 | 动态建表/查询零注入风险 |
| **安全性** | 图表安全渲染 | 三级容错解析 + 危险字段过滤（__proto__ 等） | 渲染崩溃率为 0 |
| **性能** | 轮询优化 | 指数退避算法 + Page Visibility API | 无效请求减少 60% |
| **性能** | 拖拽渲染 | CSS transform + GPU 合成层 | 60fps 流畅拖拽 |
| **性能** | 数据查询 | 动态分表 `chart_{id}`，按 ID 物理隔离 | 单表查询，互不干扰 |
| **性能** | 分页安全 | current ≥ 1，pageSize 限制 1-100 | 防止恶意全表扫描 |
| **用户体验** | 实时反馈 | WebSocket 推送 + 前端通知弹窗 | 秒级感知任务完成 |
| **用户体验** | 错误提示 | 前端中文错误提示，后端多语言异常信息 | 用户可理解的操作指引 |

Sources: [README.md](README.md#L92-L111), [ChartTaskLimiter.java](lunesnow-IntelligentBI-backend/src/main/java/com/lunesnow/manager/ChartTaskLimiter.java#L30-L50), [chartValidator.ts](lunesnow-IntelligentBI-frontend/src/utils/chartValidator.ts#L43-L90)

## 推荐阅读路径

本项目的技术文档按"快速入门 → 核心流程 → 前后端详解 → 运维管理"的递进层次组织。以下推荐路径供不同角色的开发者参考：

**如果你是初学者，想快速体验系统**：先阅读 [快速启动：本地环境搭建与运行](2-kuai-su-qi-dong-ben-di-huan-jing-da-jian-yu-yun-xing) 完成环境搭建，再查看 [API 接口一览](3-api-jie-kou-lan-cong-tu-biao-sheng-cheng-dao-guan-li-hou-tai) 和 [前端页面导航](4-qian-duan-ye-mian-dao-hang-shou-ye-tu-biao-chuang-jian-yi-biao-pan-yu-hou-tai-guan-li) 了解系统的全貌。

**如果你关注架构和整体数据流**：从 [系统架构全景](5-xi-tong-jia-gou-quan-jing-spring-boot-3-hou-duan-vue-3-qian-duan-yi-bu-xiao-xi-qu-dong) 开始，然后深入 [完整数据流水线](6-wan-zheng-shu-ju-liu-shui-xian-cong-shang-chuan-csv-excel-dao-echarts-tu-biao-de-quan-lian-lu-zhui-zong) 追踪从上传到渲染的完整链路。

**如果你是后端开发者**：按以下顺序深入后端核心模块：[图表生成控制器](7-tu-biao-sheng-cheng-kong-zhi-qi-wen-jian-xiao-yan-dong-tai-jian-biao-ren-wu-xian-liu-yu-yi-bu-ti-jiao) → [RabbitMQ 消息队列](8-rabbitmq-xiao-xi-dui-lie-ke-kao-tou-di-shou-dong-ack-yu-si-xin-dui-lie-zhong-shi-ji-zhi) → [DeepSeek AI 集成](9-deepseek-ai-ji-cheng-prompt-gong-cheng-yu-echarts-pei-zhi-zhi-neng-sheng-cheng) → [WebSocket 实时推送](10-websocket-shi-shi-tui-song-concurrenthashmap-hui-hua-guan-li-xin-tiao-jian-ce-yu-zai-xian-zhuang-tai)，然后继续学习 [动态分表](11-dong-tai-shu-ju-fen-biao-ce-lue-an-tu-biao-id-zi-dong-jian-biao-yu-shu-ju-ge-chi)、[文件解析](12-excel-csv-wen-jian-jie-xi-easyexcel-du-qu-yu-csv-ge-shi-zhuan-huan)、[SQL 防护](13-sql-zhu-ru-fang-hu-lie-ming-bai-ming-dan-xiao-yan-yu-an-quan-cha-xun-gou-jian)，最后掌握 [Lua 原子脚本](14-redis-lua-yuan-zi-jiao-ben-wu-jing-tai-tiao-jian-de-bing-fa-ren-wu-cao-wei-guan-li)、[令牌桶限流](15-redisson-ling-pai-tong-xian-liu-qi-fen-bu-shi-huan-jing-xia-jie-kou-fang-shua)、[AOP 切面](16-aop-qie-mian-bian-cheng-atauthcheck-quan-xian-xiao-yan-yu-atratelimit-xian-liu-lan-jie) 这一完整的安全防护体系。

**如果你是前端开发者**：从 [前端项目架构](17-qian-duan-xiang-mu-jia-gou-vue-3-typescript-element-plus-echarts) 开始，然后深入 [图表创建页面](18-tu-biao-chuang-jian-ye-mian-biao-dan-xiao-yan-tuo-zhuai-shang-chuan-yu-yi-bu-ren-wu-zhuang-tai-gen-zong)、[WebSocket 客户端封装](19-websocket-ke-hu-duan-feng-zhuang-zhi-shu-tui-bi-zhong-lian-xin-tiao-bao-huo-yu-zu-jian-xie-zai-qing-li)、[拖拽仪表盘](20-ke-tuo-zhuai-yi-biao-pan-bian-ji-qi-css-transform-gpu-jia-su-wu-xian-hua-bu-yu-bu-ju-chi-jiu-hua)、[在线编辑器](21-tu-biao-zai-xian-bian-ji-qi-json-shi-shi-bian-ji-echarts-an-quan-xuan-ran-yu-wei-xian-zi-duan-guo-lu) 和 [轮询优化](22-lun-xun-ce-lue-you-hua-zhi-shu-tui-bi-suan-fa-yu-page-visibility-api-zan-ting-hui-fu)。

**如果你是运维或系统管理员**：阅读 [管理后台](23-guan-li-hou-tai-yong-hu-guan-li-tu-biao-shen-ji-yu-fen-bu-shi-xian-liu-jian-kong)、[安全最佳实践](24-an-quan-zui-jia-shi-jian-bcrypt-mi-ma-ha-xi-session-jian-quan-san-ji-rong-cuo-xuan-ran-yu-fang-pa-chong) 和 [性能优化全景](25-xing-neng-you-hua-quan-jing-xiao-xi-ke-kao-tou-di-dong-tai-fen-biao-tuo-zhuai-60fps-yu-wu-xiao-qing-qiu-jian-shao-60) 了解系统的运维管理和性能特性。