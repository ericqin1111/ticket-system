# Cache 重构进度

## 已完成
- 搭建基础框架骨架：配置属性 `CacheProperties`、选项/结果模型 `CacheOptions`、`CacheResult`，函数式 `CacheLoader`、`CacheTemplate`、命中层枚举。
- 客户端封装：`CaffeineCacheClient`（本地缓存，带 Null sentinel）、`RedisCacheClient`（JSON 序列化 + 空值标记）、`TieredCacheService`（本地→Redis→DB 回源，互斥加载、防击穿）。
- Key 规范：`PriceTierCacheKeyBuilder` 采用 `ticket:tier:{id}:v{ver}` 版本化。
- 目录结构与包名与 docs 中的 `cache-framework.md` 对齐（config/core/local/remote/key/model）。
- 接入业务：`TicketServiceImpl.getPriceTierDetails` 采用 `TieredCacheService` + BloomFilter 回源；`updatePriceTier`/`evictPriceTierCacher` 调用缓存失效；移除 JetCache 注解链路。
- 新增缓存配置段 `cache.*` 至 `ticket-service` `application.yml`（TTL、负缓存、本地开关、版本号、降级开关）。

## 待办
- `TicketUpdateConsumer` 接入新的失效逻辑，移除 JetCache 残留，确认事件广播路径（Kafka/Spring Event）。
- 增补 Micrometer 指标与日志埋点（命中率、回源耗时、失败次数），慢查询告警。
- 根据 `cache-refractor.md` 收尾：结合布隆过滤器/Negative Cache，完善事件广播同步本地缓存失效；视情况加入 Redisson 锁热点兜底。
