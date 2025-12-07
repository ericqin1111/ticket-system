import json
import logging
from typing import Callable, Iterable, Optional

from .config import AppConfig
from .models import LogEvent, NormalizedEvent
from .normalizer import EventNormalizer
from .audit import AuditSink
from .filter import LogFilter

logger = logging.getLogger(__name__)

try:
    from confluent_kafka import Consumer, KafkaError
except ImportError:  # pragma: no cover
    Consumer = None
    KafkaError = None


class LogStreamConsumer:
    def __init__(
        self,
        cfg: AppConfig,
        normalizer: Optional[EventNormalizer] = None,
        audit_sink: Optional[AuditSink] = None,
        on_event: Optional[Callable[[NormalizedEvent], None]] = None,
    ):
        self.cfg = cfg
        self.normalizer = normalizer or EventNormalizer()
        self.audit_sink = audit_sink or AuditSink(cfg.audit)
        self.on_event = on_event or (lambda evt: None)
        self.log_filter = LogFilter(cfg.filter)
        self.consumer = None

    def start(self):
        if Consumer is None:
            raise RuntimeError("confluent-kafka not installed")
        kc = {
            "bootstrap.servers": self.cfg.kafka.bootstrap_servers,
            "group.id": self.cfg.kafka.group_id,
            "enable.auto.commit": self.cfg.kafka.enable_auto_commit,
            "max.poll.interval.ms": self.cfg.kafka.max_poll_interval_ms,
            "session.timeout.ms": self.cfg.kafka.session_timeout_ms,
        }
        if self.cfg.kafka.security_protocol:
            kc["security.protocol"] = self.cfg.kafka.security_protocol
        if self.cfg.kafka.sasl_mechanism:
            kc["sasl.mechanisms"] = self.cfg.kafka.sasl_mechanism
        if self.cfg.kafka.sasl_username:
            kc["sasl.username"] = self.cfg.kafka.sasl_username
        if self.cfg.kafka.sasl_password:
            kc["sasl.password"] = self.cfg.kafka.sasl_password
        self.consumer = Consumer(kc)
        self.consumer.subscribe([self.cfg.kafka.topic])
        logger.info("Kafka consumer started on topic %s", self.cfg.kafka.topic)

    def poll_and_process(self):
        if not self.consumer:
            raise RuntimeError("Consumer not started")
        msg = self.consumer.poll(1.0)
        if msg is None:
            return
        if msg.error():
            if KafkaError and msg.error().code() == KafkaError._PARTITION_EOF:  # pragma: no cover
                return
            logger.error("Kafka error: %s", msg.error())
            return
        try:
            raw_obj = json.loads(msg.value())
            raw_event = LogEvent.from_dict(raw_obj)
        except Exception as ex:  # pragma: no cover
            logger.exception("Failed to decode message: %s", ex)
            return
        norm = self.normalizer.normalize(
            raw_event, msg.topic(), msg.partition(), msg.offset()
        )
        if not norm:
            logger.debug("Dropped message due to normalization failure: %s", raw_obj)
            return
        if not self.log_filter.allow(norm):
            logger.debug(
                "Filtered out message level=%s service=%s msg=%s",
                norm.level,
                norm.service,
                norm.message,
            )
            return
        self.audit_sink.write(norm)
        self.on_event(norm)
        logger.debug(
            "Processed message level=%s service=%s offset=%s",
            norm.level,
            norm.service,
            msg.offset(),
        )
        if not self.cfg.kafka.enable_auto_commit:
            self.consumer.commit(message=msg, asynchronous=False)

    def close(self):
        try:
            if self.consumer:
                self.consumer.close()
        finally:
            self.audit_sink.close()
