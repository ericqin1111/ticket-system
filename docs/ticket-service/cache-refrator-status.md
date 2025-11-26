# Cache 重构进度

## 已完成
- 搭建基础框架骨架：配置属性 `CacheProperties`、选项/结果模型 `CacheOptions`、`CacheResult`，函数式 `CacheLoader`、`CacheTemplate`、命中层枚举。
- 客户端封装：`CaffeineCacheClient`（本地缓存，带 Null sentinel）、`RedisCacheClient`（JSON 序列化 + 空值标记）、`TieredCacheService`（本地→Redis→DB 回源，互斥加载、防击穿）。
- Key 规范：`PriceTierCacheKeyBuilder` 采用 `ticket:tier:{id}:v{ver}` 版本化。
- 目录结构与包名与 docs 中的 `cache-framework.md` 对齐（config/core/local/remote/key/model）。
- 接入业务：`TicketServiceImpl.getPriceTierDetails` 采用 `TieredCacheService` + BloomFilter 回源；`updatePriceTier`/`evictPriceTierCacher` 调用缓存失效；移除 JetCache 注解链路。
- 新增缓存配置段 `cache.*` 至 `ticket-service` `application.yml`（TTL、负缓存、本地开关、版本号、降级开关）。
- 事件广播消费者：`TicketUpdateConsumer` 改为使用缓存模板失效，去除 JetCache 依赖。
- 指标埋点：`TieredCacheService` 集成 Micrometer 统计命中（local/redis）、miss、DB 回源耗时，慢回源打印告警；`RedisMetrics` 统计 Redis GET 耗时及慢查询计数；新增 `logback-spring.xml` 便于日志控制。
- 事件同步：`updatePriceTier` 触发 Kafka `ticket_update_topic` 广播，消费者统一用缓存模板失效，完成跨实例同步。
- 失败计数与 trace tag：回源异常计数 `cache.load.fail.total`，日志包含 `cache.key` trace 信息。

## 待办
- 完善链路追踪 tag/日志格式字段；必要时加入 Redisson 锁热点兜底。
- 根据 `cache-refractor.md` 收尾：结合布隆过滤器/Negative Cache，完善事件广播同步本地缓存失效；视情况加入 Redisson 锁热点兜底。
