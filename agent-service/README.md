# Agent Service (Python)

Python 版智能预警 Agent 模块（数据流骨架）。

## 目录
- `requirements.txt`：依赖（confluent-kafka、PyYAML）。
- `config.example.yaml`：示例配置。
- `src/agent_service/`：代码
  - `main.py`：入口
  - `consumer.py`：Kafka 消费、规范化、审计
  - `normalizer.py`：字段补齐/校验
  - `models.py`：LogEvent/NormalizedEvent
  - `config.py`：配置对象
  - `audit.py`：审计写入
  - `filter.py`：级别+白名单过滤（WARN/ERROR 全量，白名单匹配 INFO 关键模式）

## 运行（示例）
```bash
pip install -r requirements.txt
KAFKA_BOOTSTRAP=localhost:29092 KAFKA_TOPIC=logs_agent_stream python -m agent_service.main
```

## 备注
- Kafka 依赖 confluent-kafka；未安装时报错提示。
- 审计默认写 `./logs/agent/audit.log`（项目根相对路径），可通过 env `AUDIT_FILE` 调整。
- Kafka 分流过滤：默认保留 WARN/ERROR，全局白名单匹配 `[CACHE MISS]`、`[BLOOM FILTER]`、`took=xxxms` 等 INFO 关键日志。
- 诊断：设置 `LOG_LEVEL=DEBUG` 可在终端看到过滤/处理的调试日志。
