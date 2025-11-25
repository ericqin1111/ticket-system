# Cache 重构进度

## 已完成
- 搭建基础框架骨架：配置属性 `CacheProperties`、选项/结果模型 `CacheOptions`、`CacheResult`，函数式 `CacheLoader`、`CacheTemplate`、命中层枚举。
- 客户端封装：`CaffeineCacheClient`（本地缓存，带 Null sentinel）、`RedisCacheClient`（JSON 序列化 + 空值标记）、`TieredCacheService`（本地→Redis→DB 回源，互斥加载、防击穿）。
- Key 规范：`PriceTierCacheKeyBuilder` 采用 `ticket:tier:{id}:v{ver}` 版本化。
- 目录结构与包名与 docs 中的 `cache-framework.md` 对齐（config/core/local/remote/key/model）。

## 待办
- 将 `TicketServiceImpl.getPriceTierDetails`、`TicketUpdateConsumer` 接入 `TieredCacheService`，替换 JetCache 注解链路，补充失效/更新流程。
- 增补配置项到 `application.yml`（cache.*），完善 Micrometer 指标/日志埋点与降级开关。
- 根据 `cache-refractor.md` 收尾：布隆过滤器结合新的缓存模板、事件广播（Kafka/Spring Event）同步本地缓存失效。
