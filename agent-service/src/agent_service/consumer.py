import json
import logging
from typing import Callable, Iterable, Optional,Any, Dict, Iterator

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
        
        logger.info("got msg offset=%s bytes=%s", msg.offset(), len(msg.value() or b""))
        try:
            raw_obj = json.loads(msg.value())
        except Exception as ex:  # pragma: no cover
            logger.exception("Failed to decode message: %s", ex)
            return
        
        processed_any = False
        cnt=0
        for raw_event in self.iter_log_events_from_payload(raw_obj):
            cnt += 1
            norm = self.normalizer.normalize(raw_event, msg.topic(), msg.partition(), msg.offset())
            if not norm:
                continue
            if not self.log_filter.allow(norm):
                continue
            self.audit_sink.write(norm)
            self.on_event(norm)
            logger.info("offset=%s expanded_logrecords=%s processed_any=%s", msg.offset(), cnt, processed_any)
            processed_any = True
        if processed_any and (not self.cfg.kafka.enable_auto_commit):
            self.consumer.commit(message=msg, asynchronous=False)


    def iter_log_events_from_payload(self,payload: Any) -> Iterator[LogEvent]:
        """
        Accepts:
        - OTLP JSON dict: {"resourceLogs":[{"scopeLogs":[{"logRecords":[...]}]}]}
        - list (rare): [ ... ]  (we'll traverse)
        - plain dict (non-OTLP): {"level":..., "message":...}
        Yields LogEvent per logRecord (for OTLP) or one event otherwise.
        """
        # Some producers might wrap multiple messages as a list
        if isinstance(payload, list):
            for item in payload:
                yield from self.iter_log_events_from_payload(item)
            return

        # Non-dict payload -> cannot parse structurally, fall back to raw string
        if not isinstance(payload, dict):
            yield LogEvent.from_dict({"message": str(payload), "raw": {"payload": payload}})
            return

        # OTLP payload
        if "resourceLogs" in payload:
            resource_logs = payload.get("resourceLogs") or []
            for rl in resource_logs:
                scope_logs = rl.get("scopeLogs") or rl.get("scope_logs") or []
                for sl in scope_logs:
                    log_records = sl.get("logRecords") or sl.get("log_records") or []
                    for lr in log_records:
                        # build a minimal OTLP wrapper containing exactly one logRecord
                        one = {
                            "resourceLogs": [{
                                "resource": rl.get("resource", {}),
                                "scopeLogs": [{
                                    "scope": sl.get("scope", {}),
                                    "logRecords": [lr],
                                }],
                            }]
                        }
                        yield LogEvent.from_dict(one)
            return

        # Non-OTLP dict -> single event
        yield LogEvent.from_dict(payload)

    def close(self):
        try:
            if self.consumer:
                self.consumer.close()
        finally:
            self.audit_sink.close()

