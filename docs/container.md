# 中间件容器清单（docker-compose）

| 名称 | 镜像/版本 | 端口(宿主机) | 主要用途 | 数据卷/备注 |
| --- | --- | --- | --- | --- |
| mysql-ticket | mysql:8.0 | 3306 | 业务库 `ticket_system`，订单/库存/票务/用户数据 | `mysql-data:/var/lib/mysql`，挂载 `~/conf/mysql/my.cnf` |
| redis-ticket | redis:7.0 | 6379 | 二级缓存、布隆过滤器、排队/通行证、库存预扣 | `redis-data:/data`，密码 `123456` |
| zookeeper-ticket | confluentinc/cp-zookeeper:7.3.0 | 2181 | Kafka 依赖 | - |
| kafka-ticket | confluentinc/cp-kafka:7.3.0 | 29092(宿主机),9092(容器) | 消息队列；Debezium CDC 输出、缓存失效广播 | 依赖 zookeeper；健康检查 kafka-topics |
| nacos-ticket | nacos/nacos-server:2.2.3 | 8848,9848,9849 | 注册发现与配置中心 | `nacos-logs:/home/nacos/logs` |
| prometheus-ticket | prom/prometheus:2.45.0 | 9090 | 指标采集（Micrometer） | `./prometheus/prometheus.yml` 挂载，`prometheus-data:/prometheus` |
| grafana-ticket | grafana/grafana:9.5.3 | 3000 | 指标可视化 | `grafana-data:/var/lib/grafana`，默认账号 admin/admin |
| loki | grafana/loki:2.9.2 | 3100 | 日志聚合 | - |
| alloy | grafana/alloy:1.2.1 | - | 日志采集分流（Loki + Kafka `logs_agent_stream`） | 挂载 `./agent-flow/config.river` 与 `./logs` |
| connect-ticket | debezium/connect:2.1 | 18083 | Debezium Kafka Connect，同步 MySQL Binlog 至 Kafka | 依赖 kafka；topics: configs/offsets/status |
| connect-configurator-ticket | curlimages/curl:latest | - | 启动时自动调用 Connect API 注册 Debezium 任务 | 挂载 `./connect-conf` ，失败自动重试 |

## 网络与卷
- 网络：`ticket-system-net` (bridge) 供所有容器互通。
- 数据卷：`mysql-data`、`redis-data`、`nacos-logs`、`prometheus-data`、`grafana-data`（由 compose 管理）。
