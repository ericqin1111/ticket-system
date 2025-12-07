import time
import json
from typing import Optional
from .models import LogEvent, NormalizedEvent


class EventNormalizer:
    """Normalize raw LogEvent into a typed NormalizedEvent, dropping invalid rows."""

    def normalize(
        self,
        event: LogEvent,
        topic: str,
        partition: int,
        offset: int,
    ) -> Optional[NormalizedEvent]:
        message = event.message or json.dumps(event.raw, ensure_ascii=False)
        level = (event.level or "INFO").upper()
        service = event.service or event.app or "unknown"
        trace_id = event.trace_id or "-"
        ingest_ts = str(int(time.time() * 1000))
        return NormalizedEvent(
            timestamp=event.timestamp or ingest_ts,
            service=service,
            level=level,
            logger_name=event.logger_name or "",
            message=message,
            trace_id=trace_id,
            cache_layer=event.cache_layer,
            app=event.app,
            stack_trace=event.stack_trace,
            ingest_ts=ingest_ts,
            topic=topic,
            partition=partition,
            offset=offset,
            raw=event.raw,
        )
