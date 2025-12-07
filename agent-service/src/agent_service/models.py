from dataclasses import dataclass
from typing import Optional, Dict, Any
import json


@dataclass
class LogEvent:
    timestamp: Optional[str]
    service: Optional[str]
    level: Optional[str]
    logger_name: Optional[str]
    message: Optional[str]
    trace_id: Optional[str]
    cache_layer: Optional[str]
    app: Optional[str]
    stack_trace: Optional[str]
    raw: Dict[str, Any]

    @staticmethod
    def from_dict(data: dict) -> "LogEvent":
        # Handle OTLP-style payloads (resourceLogs -> scopeLogs -> logRecords)
        if "resourceLogs" in data:
            try:
                rl = data["resourceLogs"][0]
                sl = rl.get("scopeLogs", rl.get("scope_logs", []))[0]
                lr = sl.get("logRecords", sl.get("log_records", []))[0]
            except Exception:
                lr = None
            if lr:
                attrs_list = lr.get("attributes") or []
                attrs: Dict[str, Any] = {}
                for item in attrs_list:
                    key = item.get("key")
                    val = item.get("value", {})
                    if "stringValue" in val:
                        attrs[key] = val["stringValue"]
                body = lr.get("body", {})
                body_str = body.get("stringValue") or body.get("string_value") or ""
                parsed_line = None
                if isinstance(body_str, str):
                    try:
                        parsed_line = json.loads(body_str)
                    except Exception:
                        parsed_line = None
                message = (
                    (parsed_line.get("message") if isinstance(parsed_line, dict) else None)
                    or body_str
                )
                filename = attrs.get("log.file.path") or attrs.get("filename") or attrs.get("log.file.name")
                service_attr = attrs.get("service") or (
                    filename.split("/")[-1].split(".")[0] if filename else None
                )
                return LogEvent(
                    timestamp=lr.get("timeUnixNano") or lr.get("observedTimeUnixNano"),
                    service=service_attr,
                    level=attrs.get("level") or (parsed_line.get("level") if isinstance(parsed_line, dict) else None),
                    logger_name=attrs.get("logger_name")
                    or attrs.get("logger")
                    or (parsed_line.get("logger_name") if isinstance(parsed_line, dict) else None),
                    message=message,
                    trace_id=attrs.get("traceId") or attrs.get("trace_id"),
                    cache_layer=attrs.get("cache_layer") or (parsed_line.get("cache_layer") if isinstance(parsed_line, dict) else None),
                    app=parsed_line.get("app") if isinstance(parsed_line, dict) else None,
                    stack_trace=(parsed_line.get("stack_trace") if isinstance(parsed_line, dict) else None),
                    raw=data,
                )

        # Support Alloy Kafka exporter payloads and raw JSON logs
        labels = data.get("labels", {}) if isinstance(data.get("labels"), dict) else {}
        attributes = data.get("attributes", {}) if isinstance(data.get("attributes"), dict) else {}
        line = data.get("line")
        parsed_line = None
        if isinstance(line, str):
            try:
                parsed_line = json.loads(line)
            except Exception:
                parsed_line = None
        # choose message from parsed line if available, else use line field
        message = (
            data.get("message")
            or (parsed_line.get("message") if isinstance(parsed_line, dict) else None)
            or line
        )
        stack_trace = data.get("stack_trace") or data.get("exception")
        if isinstance(parsed_line, dict):
            stack_trace = stack_trace or parsed_line.get("stack_trace") or parsed_line.get("exception")
        def pick(key: str):
            return (
                data.get(key)
                or labels.get(key)
                or attributes.get(key)
                or (parsed_line.get(key) if isinstance(parsed_line, dict) else None)
            )
        return LogEvent(
            timestamp=data.get("timestamp") or data.get("time") or data.get("ts"),
            service=pick("service"),
            level=pick("level"),
            logger_name=data.get("logger_name")
            or labels.get("logger_name")
            or data.get("logger")
            or attributes.get("logger_name")
            or (parsed_line.get("logger_name") if isinstance(parsed_line, dict) else None),
            message=message,
            trace_id=pick("traceId") or pick("trace_id"),
            cache_layer=pick("cache_layer") or pick("cache.layer"),
            app=pick("app"),
            stack_trace=stack_trace,
            raw=data,
        )
        return LogEvent(
            timestamp=data.get("timestamp"),
            service=data.get("service"),
            level=data.get("level"),
            logger_name=data.get("logger_name") or data.get("logger"),
            message=data.get("message"),
            trace_id=data.get("traceId") or data.get("trace_id"),
            cache_layer=data.get("cache_layer") or data.get("cache.layer"),
            app=data.get("app"),
            stack_trace=data.get("stack_trace") or data.get("exception"),
            raw=data,
        )

    def to_json(self) -> str:
        return json.dumps(self.raw, ensure_ascii=False)


@dataclass
class NormalizedEvent:
    timestamp: str
    service: str
    level: str
    logger_name: str
    message: str
    trace_id: str
    cache_layer: Optional[str]
    app: Optional[str]
    stack_trace: Optional[str]
    ingest_ts: str
    topic: str
    partition: int
    offset: int
    raw: dict
