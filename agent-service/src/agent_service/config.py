from dataclasses import dataclass
from typing import Optional, List
import os
import yaml


@dataclass
class KafkaConfig:
    bootstrap_servers: str
    group_id: str = "agent-log-consumer"
    topic: str = "logs_agent_stream"
    enable_auto_commit: bool = False
    max_poll_records: int = 200
    max_poll_interval_ms: int = 300000
    session_timeout_ms: int = 10000
    security_protocol: Optional[str] = None
    sasl_mechanism: Optional[str] = None
    sasl_username: Optional[str] = None
    sasl_password: Optional[str] = None


@dataclass
class AuditConfig:
    enabled: bool = True
    file_path: str = "./logs/agent/audit.log"
    sampling_rate: float = 1.0  # 0-1


@dataclass
class FilterConfig:
    levels: List[str]
    whitelist_patterns: List[str]

    @staticmethod
    def default() -> "FilterConfig":
        return FilterConfig(
            levels=["ERROR", "WARN"],
            whitelist_patterns=[
                "\\[CACHE MISS\\]",
                "\\[BLOOM FILTER\\]",
                "took=\\d+ms",
            ],
        )


@dataclass
class AppConfig:
    kafka: KafkaConfig
    audit: AuditConfig
    filter: FilterConfig

    @staticmethod
    def load(path: str) -> "AppConfig":
        with open(path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
        kafka = KafkaConfig(**data["kafka"])
        audit_cfg = AuditConfig(**data.get("audit", {}))
        filter_cfg = (
            FilterConfig(**data.get("filter", {}))
            if data.get("filter")
            else FilterConfig.default()
        )
        return AppConfig(kafka=kafka, audit=audit_cfg, filter=filter_cfg)

    @staticmethod
    def from_env() -> "AppConfig":
        kafka = KafkaConfig(
            bootstrap_servers=os.getenv("KAFKA_BOOTSTRAP", "localhost:29092"),
            topic=os.getenv("KAFKA_TOPIC", "logs_agent_stream"),
            group_id=os.getenv("KAFKA_GROUP", "agent-log-consumer"),
            enable_auto_commit=False,
        )
        audit_cfg = AuditConfig(
            enabled=os.getenv("AUDIT_ENABLED", "true").lower() == "true",
            file_path=os.getenv("AUDIT_FILE", "./logs/agent/audit.log"),
        )
        filter_cfg = FilterConfig.default()
        return AppConfig(kafka=kafka, audit=audit_cfg, filter=filter_cfg)
