# Ticket 模块缓存重构方案（cache-refractor）

目标：替换/增强当前 JetCache 隐式管理，将缓存链路透明化、可观测、可扩展，采用本地 Caffeine + Redis + DB 的多级缓存，明确 Key 规范、生命周期、回源/回填策略与一致性保障。

## 1. 现状小结（痛点）
- 使用 JetCache 注解缓存 priceTierDetail，缓存穿透/击穿保护由注解配置，逻辑分散且可观测性弱。
- 缓存 Key/TTL/失效流程在注解中隐式声明，难以跨团队统一，运维排查成本高。
- 更新/失效依赖注解 `@CacheUpdate/@CacheInvalidate` 及 Kafka 通知，更新链路未对外显式暴露监控指标。
- 多级缓存策略（本地 vs Redis）不可定制：无法精细调节冷热分级、容量、淘汰策略、降级策略。

## 2. 重构目标
- 显式实现“查询 → 本地缓存 → Redis → DB”链路，统一封装 CacheManager（本地 Caffeine，远程 Redis）。
- 统一 Key 设计、TTL、容量与统计指标；可配置热数据放大策略（权重/TTL），支持旁路写与主动失效。
- 明确一致性策略：写路径优先落库 + 失效 Redis + 清理本地缓存；读路径支持布隆过滤器 + 双删/延时删除兜底。
- 提升可观测性：暴露缓存命中率、加载耗时、回源次数、回源失败数；记录 Redis 回源慢日志；埋点链路追踪。

## 3. 目标架构
```
Controller/Service
   ↓
CacheService (统一接口)
   ├─ CaffeineCache (本地，TTL/size/统计)
   ├─ RedisCache (远程，TTL/压缩/JSON序列化)
   └─ Loader(DB回源，含布隆过滤 & 降级策略)
```
- 访问顺序：Caffeine 命中 → Redis 命中 → DB 加载并写回 Redis，再写回 Caffeine。
- Key 规范：`ticket:tier:{tierId}:v{ver}`（ver 用于版本化失效，兼容灰度/Schema 变更）。
- 序列化：统一 Jackson + JDK8 time module；可选 gzip/ snappy 压缩大对象。
- TTL：默认 1h；本地 Caffeine 10~30m；Redis 可按热度动态延长（HotKey 扩散）。

## 4. 设计细节
### 4.1 统一缓存接口
- 定义 `CacheTemplate<T>`：`get(key, loader, opts)`；内部实现两级查找、回源、填充、统计。
- 支持批量接口（后续优化）：`getAll(keys, batchLoader)`，减少 N 次 Redis/DB 往返。

### 4.2 本地缓存（Caffeine）
- 配置项：最大条目（或权重）、expireAfterWrite/Access、recordStats。
- 预留 onRemoval 钩子记录淘汰原因（size/expired）。
- 热点防穿透：当本地返回 Null 时带上短期 Negative Cache TTL（如 30s）。

### 4.3 Redis 缓存
- Key：`ticket:tier:{tierId}:v{ver}`；Value：JSON 文本。
- TTL：默认 1h，Negative Cache 30s；允许通过配置对热 Key 延长到 3h。
- Pipeline：批量读写（mget + pipeline setex），降低网络开销。
- 一致性：
  - 读：如果 Redis miss，DB 加载后 setex；
  - 写：先写库 → 删 Redis → 删本地 Caffeine（或 bump 版本号），必要时延时双删（如 500ms）。

### 4.4 布隆过滤器
- 保留 Redisson BloomFilter：在 Redis miss 前先检查 ID 合法性；非法 ID 直接本地短 TTL negative cache，降低 DB/Redis 压力。

### 4.5 更新/失效策略
- 写接口（更新价档）流程：
  1) 校验 & 写 DB；
  2) 删除 Redis Key 或提升版本号 `v`；
  3) 删除 Caffeine；
  4) 发出异步失效事件（Kafka 或本地事件），用于多实例同步清理本地缓存。
- 读接口防并发回源：本地用 `Caffeine.async`/`Map.computeIfAbsent` + `CompletableFuture`，或用 Redisson 分布式锁对热点 miss 进行抑制；默认使用本地级别的互斥即可。

### 4.6 观测与告警
- 暴露指标（Micrometer）：`cache.hit/local/redis/db`, `cache.load.time`, `cache.failures`, `cache.evictions`, `cache.negative_hits`。
- 日志：回源 DB/Redis 超过阈值（例如 50ms/10ms）打印 WARN；回源异常打印 ERROR。
- Trace：在缓存命中/回源时记录 Trace Tag：`cache.layer=local/redis/db`，便于链路检索。

### 4.7 热点与降级
- 热点自动扩散：当 Redis miss + QPS 高时可直接返回 Redis 层的兜底值（如「请重试」）避免 DB 雪崩。
- 降级开关：配置 `cache.read.only=true` 时，写接口只做失效，不阻塞读；`cache.disable=true` 时旁路缓存直接 DB。

## 5. 落地步骤
1) 新建 `cache` 包：`CacheTemplate`, `CacheOptions`, `TieredCacheService`；封装 Caffeine/Redis 操作与统计。
2) 抽取 Key 生成器：`PriceTierCacheKeyBuilder`，兼容版本号；在配置中预留版本 bump。
3) 服务改造：`TicketServiceImpl.getPriceTierDetails` 使用 `cacheTemplate.get(key, loader)` 实现多级缓存；回源 loader 继续使用 BloomFilter 和现有 DB 组装逻辑。
4) 更新/失效：`updatePriceTier` 改为「写库 → invalidation（Redis/Caffeine）→ 发布 Kafka invalidation 事件」；消费者 `TicketUpdateConsumer` 清理本地缓存即可，无需 JetCache 注解。
5) 配置化：在 `application.yml` 增加 `cache.local.*`（size/expire）、`cache.redis.*`（ttl/compress）、`cache.key.version` 等。
6) 观测：接入 Micrometer，暴露到 Prometheus；增加日志切面或拦截器打印 cache layer；在 PLG/Loki 中增加字段。
7) 回滚/兼容：首期可保留 JetCache 但关闭 `@Cached`，两套对比一段时间；或灰度启用新缓存并通过 `v` 版本区分键。

## 6. 风险与缓解
- 回源放大：热点同时穿透两级缓存；使用本地互斥 + 可选 Redisson 锁。
- 版本膨胀：频繁变更导致版本号偏移，需定期重建或对高频更新场景使用“删 Key + 延时双删”。
- 序列化兼容：JSON 字段变更需配合版本号或保留向后兼容字段；上线前做全量回填或预热。
- 一致性窗口：删 Redis 后到删本地缓存的时间差；通过事件广播 + 延时双删降低残留命中。

## 7. 验收点
- 命中率 & RT：本地命中率预期 >70%（热点场景），P99 RT 较现状降低；Redis miss -> DB 成功率 99.9%。
- 观测：Prometheus 可见命中率/回源次数；Loki 可按 `cache.layer` 搜索；Trace 上能看到缓存层标签。
- 功能：读写一致；更新后 2s 内缓存失效；布隆过滤器拦截非法 ID。

