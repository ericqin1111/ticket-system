# Develop Status

- Exposed `management.endpoints.web.exposure` and enabled `/actuator/prometheus` in `ticket-service/src/main/resources/application.yml` for cache指标输出。
- Added Prometheus scrape job `ticket-service` (`/actuator/prometheus`) in `prometheus/prometheus.yml` to收集服务侧指标。
- Renamed cache counter metric bases to `cache.hit/cache.miss/cache.load.fail` (avoid double `_total_total` in Prometheus) in `ticket-service` cache service to align查询名称。
- Removed JetCache residuals: deleted jetcache dependency from `ticket-service/pom.xml`, removed JetCache enable annotations from `TicketServiceApplication`, and purged `jetcache:` config block in `ticket-service/src/main/resources/application.yml` per jetcache-deletion plan。
