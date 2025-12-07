import json
import os
import logging
from typing import Optional
from .models import NormalizedEvent
from .config import AuditConfig

logger = logging.getLogger(__name__)


class AuditSink:
    """Simple JSON lines audit writer."""

    def __init__(self, cfg: AuditConfig):
        self.cfg = cfg
        self._fh = None
        if cfg.enabled:
            path = cfg.file_path
            # Normalize to absolute path to avoid surprises with CWD
            if not os.path.isabs(path):
                path = os.path.abspath(path)
            directory = os.path.dirname(path)
            try:
                os.makedirs(directory, exist_ok=True)
                self._fh = open(path, "a", encoding="utf-8")
                logger.info("Audit sink enabled at %s", path)
            except Exception as ex:
                logger.error("Failed to open audit file %s: %s", path, ex)
                self._fh = None

    def write(self, evt: NormalizedEvent):
        if not self.cfg.enabled or not self._fh:
            return
        # Sampling not implemented; keep placeholder for future use.
        payload = {
            "timestamp": evt.timestamp,
            "service": evt.service,
            "level": evt.level,
            "traceId": evt.trace_id,
            "cache_layer": evt.cache_layer,
            "topic": evt.topic,
            "partition": evt.partition,
            "offset": evt.offset,
            "raw": evt.raw,
        }
        self._fh.write(json.dumps(payload, ensure_ascii=False) + "\n")
        self._fh.flush()

    def close(self):
        if self._fh:
            self._fh.close()
            self._fh = None
