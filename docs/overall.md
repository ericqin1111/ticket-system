# 购票通项目总体理解（overall）

## 项目概览
- 技术栈：Spring Boot 3.1 + Spring Cloud 2022 + Nacos 注册中心 + Gateway 网关 + OpenFeign；数据层 MySQL + MyBatis-Plus；缓存 Redis（多级缓存 JetCache+Caffeine/Redis）、Redisson；消息 Kafka（Outbox/Debezium）、RocketMQ（延时队列预留）；安全 JWT。
- 目标：高并发购票的库存一致性、防超卖、排队削峰、订单超时处理、统一认证鉴权、缓存加速、链路追踪（PLG/Grafana+Loki）。
- 模块：gateway-service、user-service、ticket-service、order-service、inventory-service、ticketing-common（Redis key/队列常量），以及支撑目录（docker-compose 依赖、prometheus/promtail/rocketmq 等）。

## 模块职责总览
- **gateway-service** (`gateway-service/src/main/...`)
  - WebFlux Gateway 路由到 order/user/ticket 等服务（`application.yml` routes）。
  - `AuthGlobalFilter` + `JwtUtil`：校验 JWT，白名单 `auth.skip-urls`，把用户信息透传到下游 `X-User-ID/Name`。
  - `QueueGatewayFilter` + 策略 `OrderCreateQueueStrategy`：针对下单接口 `/api/orders/create` 做排队/通行证校验，读取 Redis 队列配置 `config:ticket_item:{id}`，校验放行 token（`queue:ticket_item:{id}:user:{userId}`），否则返回 425 提示排队。
- **user-service** (`user-service/src/main/...`)
  - 用户注册/登录接口 `/user/register`、`/user/login`。
  - 密码 `BCryptPasswordEncoder`，登录生成 JWT（`JwtUtil`，密钥/过期配置 `application.yml`），其余接口默认需鉴权（Spring Security 无状态配置）。
- **ticket-service** (`ticket-service/src/main/...`)
  - 票务/场次/档位数据查询与维护。
  - JetCache 多级缓存（本地+Caffeine+Redis）缓存价档详情 `@Cached priceTierCache:`，`@CachePenetrationProtect` 防击穿，`@CacheInvalidate/Update` 做更新/失效。
  - Redisson BloomFilter (`BloomFIlterInitializer`) 预加载所有价档 ID，非法 ID 直接拦截。
  - `TicketUpdateConsumer` 监听 Kafka `ticket_update_topic` 清理缓存（发布逻辑预留）。
- **order-service** (`order-service/src/main/...`)
  - 下单入口 `/orders/create`：使用请求头 `X-User-ID` 和 `X-Ticket-Item-Id`，Redis 10s 锁防重复提交；Feign 调用库存服务执行 Redis 预扣；写订单表为“处理中”；写 Outbox 事件。
  - Outbox + Debezium/Kafka：`Outbox` 表持久化事件，Debezium binlog => Kafka 主题 `mysql_server_ticket.ticket_system.outbox`；`OrderKafkaConsumer` 消费后走 `processOrderCreation`。
  - `processOrderCreation`：调用库存服务落库扣减（幂等由 `stock_deduction_log` 控制）；成功则订单状态更新为已完成，否则回滚缓存。
  - 排队与放号：`QueueService`/`QueueController` 提供 `/queue/enter`、`/queue/status`，使用 Redis ZSET 记录排队；`ReleaseProcessor` 定时任务按 `config:ticket_item:{id}.release_rate` 批量发放通行证 token（5 分钟 TTL）并移出队列；`QueueAdminController` 允许配置是否排队、放行速率及活跃票集合 `config:queue_active_tickets`。
  - 预留 RocketMQ 延时队列（目录 rocketmq）用于订单超时关闭。
- **inventory-service** (`inventory-service/src/main/...`)
  - 库存 Redis 预扣减 `preDeductStockInCache`（失败则回补）；数据库扣减 `deductStockInDB` 使用 `UPDATE … quantity >=` 防超卖，并写 `stock_deduction_log` 做幂等。
  - 预热：`StockerCacheWarmer` 启动从 DB 加载全部库存到 Redis `stock:ticket_item:{id}`。
  - 回滚接口补偿缓存。
- **ticketing-common**
  - Redis key 约定（队列/通行证/库存/价档缓存），队列 key 构造辅助。

## 核心业务流（下单链路）
1) 客户端访问 `/api/orders/create`（带 JWT，头部含 `X-Ticket-Item-Id`）。
2) Gateway：`AuthGlobalFilter` 校验 JWT -> `QueueGatewayFilter.OrderCreateQueueStrategy` 检查是否需要排队/通行证。
3) Order 服务：
   - Redis 锁防重复 -> Feign 预扣库存（Redis）失败则返回库存不足。
   - 生成 `orderSn`，写订单（状态处理中），写 Outbox 事件（消息体含 userId/ticketItemId/quantity/orderSerialNo）。
4) Debezium 将 Outbox binlog 投递 Kafka；`OrderKafkaConsumer` 读取 `payload` -> `processOrderCreation`：
   - 调库存 DB 扣减（幂等日志）；成功则订单状态更新为已完成，失败则补回缓存。
5) `ReleaseProcessor` 周期性按放行速率从排队 ZSET 批量发 token，令牌一次性消费（网关校验后删除）。

## 数据模型（摘自 `docs/db.sql`，单库 ticket_system）
- `users` 用户表。
- 票务侧：`tickets`(SPU)、`events`(场次)、`price_tiers`(价档/票档)、`ticket_items`(SKU 示例)、`stock`(库存)、`stock_deduction_log`(扣减幂等日志)。
- 订单侧：`orders`、`order_items`、`outbox`（事务消息表）。

## 关键配置与依赖
- 注册 & 配置：Nacos `localhost:8848`，Gateway 启用服务发现动态路由。
- Redis：默认 `localhost:6379`，密码 123456，队列/库存/缓存/布隆过滤器依赖。
- MySQL：`jdbc:mysql://127.0.0.1:3306/ticket_system`，根账户 root/root。
- Kafka：消费主题 `mysql_server_ticket.ticket_system.outbox`（Debezium），`ticket_update_topic`（缓存失效）。
- JWT 密钥：Base64 字符串（网关与用户服务共用）。

## 运行与排查提示
- 启动顺序建议：Nacos/Redis/Kafka/MySQL → user-service → ticket-service → inventory-service → order-service → gateway-service；确保 Debezium/CDC 任务将 outbox 投递到 Kafka。
- 观察点：
  - 库存一致性：`stock` 与 Redis 预扣对齐，`stock_deduction_log` 是否记录；
  - 队列放号：`config:queue_active_tickets` 集合、`config:ticket_item:{id}` 中 `is_active`/`release_rate`；`queue:ticket_item:{id}` 排队长度；
  - JWT：网关与用户服务的 `jwt.secret`/`expiration` 一致；
  - Outbox：表写入后 Kafka 订阅 `mysql_server_ticket.ticket_system.outbox`。

