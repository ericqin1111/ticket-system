# Ticket 缓存重构待办清单（schedule）

依据 `cache-refractor.md`、`cache-framework.md` 与 `cache-refrator-status.md`，下一步实施事项：

1) 接入新缓存模板
- 将 `TicketServiceImpl.getPriceTierDetails` 改为使用 `TieredCacheService` + `PriceTierCacheKeyBuilder`，保留布隆过滤器校验，移除 JetCache 注解。
- 在回源 loader 中复用 DB/组装逻辑，命中后写回本地+Redis。

2) 缓存失效/更新链路
- `updatePriceTier`：写库后调用 `cacheTemplate.invalidate(key)`，发布失效事件（Kafka/Spring Event）同步各实例本地缓存。
- `TicketUpdateConsumer`：改为只做本地失效，去除 JetCache 依赖。
- 考虑延时双删（可选）或版本号 bump。

3) 配置与观测
- 在 `application.yml` 添加 `cache.*` 配置（TTL、negative TTL、local 开关、版本号、降级开关等）。
- 接入 Micrometer 指标与日志：记录命中层、回源耗时、失败次数；慢查询阈值告警。

4) 并发与降级
- 保留本地互斥防击穿，必要时在热点 miss 使用 Redisson 锁兜底（由配置开关控制）。
- Redis 异常/序列化失败的降级策略，按照 `CacheOptions.allowDegrade` 处理。

5) 事件与布隆过滤
- 布隆过滤器前置检查非法 tierId，空值写入短 TTL negative cache。
- 失效事件使用 Kafka topic 或 Spring Event 广播，确保多实例本地缓存同步删除。

6) 清理与文档
- 移除 JetCache 注解/配置残留（在完成替换后）。
- 更新 `cache-refrator-status.md` 记录进展；如有新的参数/指标，补充 `cache-framework.md`。
