# Ticket 模块多级缓存框架设计（cache-framework）

基于 `cache-refractor.md` 的实施蓝图，明确包结构、类/接口职责、关键流程、配置与扩展点。目标是替换 JetCache 注解式缓存，提供可观测、可控的 Caffeine + Redis + DB 多级缓存。

## 1. 包与文件结构（建议）
```
org.example.ticket.cache
├─ key
│   └─ PriceTierCacheKeyBuilder
├─ model
│   ├─ CacheOptions
│   └─ CacheMetrics (指标快照 DTO，可选)
├─ local
│   └─ CaffeineCacheClient
├─ remote
│   └─ RedisCacheClient
├─ core
│   ├─ CacheTemplate
│   ├─ CacheLoader<T>
│   ├─ CacheLayer (enum: LOCAL/REDIS/DB)
│   └─ TieredCacheService (具体实现)
├─ event
│   ├─ CacheInvalidationPublisher
│   └─ CacheInvalidationListener
├─ config
│   ├─ CacheProperties (绑定 yml)
│   ├─ CacheConfig (装配 Caffeine/Redis beans)
│   └─ MetricsConfig (可选，Prometheus)
└─ instrumentation
    ├─ CacheTracer (打点/trace tag)
    └─ CacheLogger (慢日志)
```

## 2. 核心接口与类职责
- `CacheTemplate`：统一入口，方法 `T get(String key, CacheOptions opts, CacheLoader<T> loader)`；内部完成本地→Redis→DB 回源、回填、统计、异常处理。
- `CacheLoader<T>`：函数式接口，定义 `T load()`，用于 DB/聚合查询。
- `CacheOptions`：每次查询的策略参数，含 TTL 覆盖、negative TTL、是否启用布隆过滤器、是否允许降级等。
- `CacheLayer`：枚举，标记命中层级，用于指标与日志。
- `TieredCacheService`：`CacheTemplate` 的默认实现，组合 `CaffeineCacheClient`、`RedisCacheClient`、`CacheTracer`，处理互斥与回源。
- `CaffeineCacheClient`：封装本地缓存（大小、expire、stats、淘汰监听）。
- `RedisCacheClient`：封装 Redis 读写、批量 mget/pipeline、setex、negative cache、版本化 Key 支持。
- `PriceTierCacheKeyBuilder`：生成 `ticket:tier:{tierId}:v{ver}`，并暴露 `bumpVersion()` 方法供失效时使用。
- `CacheInvalidationPublisher/Listener`：通过 Kafka / Spring 事件广播本地缓存失效，实现多实例同步。
- `CacheTracer`：写入 Micrometer 指标和 trace tag，如 `cache.hit`, `cache.miss`, `cache.layer`；`CacheLogger` 记录 Redis/DB 慢查询。

## 3. 关键流程（get）
1. **布隆过滤检查（可选）**：`RBloomFilter.contains(id)`，失败则返回 null 并写本地短 TTL negative cache。
2. **本地缓存查找**：命中返回；未命中继续。
3. **Redis 查找**：命中则反序列化并写回本地（短 TTL）；未命中继续。
4. **回源加载**：调用 `loader.load()`（访问 DB + 组装 DTO）；
   - 成功：写 Redis（setex，带版本号），写本地；返回数据。
   - 为空：写 Redis negative cache（短 TTL），写本地 negative。
   - 异常：记录 error 指标，按 `CacheOptions.allowDegrade` 决定是否返回空或抛出。
5. **互斥/击穿保护**：本地 `ConcurrentHashMap<key, CompletableFuture>` 或 Caffeine `asyncCache` 限制同 key 并发回源；必要时可在 Redis 加分布式锁兜底。

## 4. 写入/失效流程
- 写路径（更新价档）：
  1) 写 DB 成功；
  2) 失效 Redis：删除 key 或 bump version；
  3) 失效本地：`Caffeine.invalidate(key)`；
  4) 发布失效事件（Kafka/Spring Event），其他实例清理本地缓存。
- 延时双删：可选在步骤 2 之后延迟 500ms 再删 Redis，降低并发脏读。

## 5. 运行栈关联（docker-compose）
- MySQL 8.0（`mysql-ticket`，`ticket_system` 库）作为主存储/回源 DB。
- Redis 7.0（`redis-ticket`）作为二级缓存与布隆过滤器/队列存储，密码 `123456`。
- Kafka 7.3.0 + Zookeeper（`kafka-ticket`/`zookeeper-ticket`）承载 Debezium CDC 事件和缓存失效广播。
- Debezium Connect 2.1（`connect-ticket` + `connect-configurator-ticket`）将 Outbox/binlog 推送至 Kafka。
- Nacos 2.2.3（注册/配置中心）提供配置下发（可托管缓存 TTL/版本）。
- 观测：Prometheus 2.45.0 + Grafana 9.5.3 + Loki/Promtail 2.9.2，用于采集 Micrometer 指标、慢日志和链路追踪字段（`cache.layer` 等）。

## 6. 配置项（`application.yml` 示例）
```yaml
cache:
  key:
    tier-version: 1 # bump 触发全局失效
  local:
    enabled: true
    maximum-size: 50000
    expire-after-write-ms: 20_000
    negative-ttl-ms: 30_000
  redis:
    ttl-ms: 3_600_000
    negative-ttl-ms: 30_000
    hot-key-ttl-ms: 10_800_000 # 热点可延长
    compress: false
  breaker:
    degrade-on-error: true
    use-redis-lock-for-penalty: false
```

## 7. 序列化与压缩
- 使用统一 `ObjectMapper` (JavaTimeModule, snake_case 可选)；
- 对 Value 进行 JSON 序列化；可配置 gzip/snappy（当对象 > N KB 时启用）。

## 8. 观测与日志
- 指标：
  - `cache_hit_total{layer="local|redis"}`
  - `cache_miss_total{layer="local|redis"}`
  - `cache_load_total` / `cache_load_fail_total`
  - `cache_load_duration_ms_bucket`
  - `cache_negative_hit_total`
- 日志：
  - Redis/DB 超过阈值（10ms/50ms）WARN。
  - 回源异常 ERROR。
- Trace：在 Span/Trace 上添加 tag `cache.layer`、`cache.key`（脱敏）、`cache.hit`。

## 9. 并发与错误处理
- 本地互斥防击穿：`pendingLoads.computeIfAbsent(key, ...)`，完成后移除。
- Redis 异常时：记录降级指标，按配置走本地缓存兜底或直连 DB。
- 反序列化失败：删除 Redis 旧值，回源重建。

## 10. 迁移/兼容策略
- 第一阶段：引入 `CacheTemplate` 并在 `TicketServiceImpl.getPriceTierDetails` 替换 JetCache；保留 JetCache 注解但禁用（或快速对比）。
- 第二阶段：删除 JetCache 注解，全面使用新缓存；`TicketUpdateConsumer` 改为仅做本地失效。
- 版本化失效：通过 `cache.key.tier-version` 统一 bump，可在发布前修改配置完成全局失效。

## 11. 验收清单
- 核验命中率、回源次数、RT 指标可在 Prometheus 中查看。
- 更新价档后 2s 内缓存失效；多实例保持一致。
- 布隆过滤器拦截非法 tierId，负缓存 TTL 生效。
- 回源异常时按配置降级，不影响主链路稳定性。
