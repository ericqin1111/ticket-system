import re
from typing import List
from .models import NormalizedEvent
from .config import FilterConfig


class LogFilter:
    """Filter events by level and whitelist patterns for INFO-like signals."""

    def __init__(self, cfg: FilterConfig):
        self.levels = {lvl.upper() for lvl in cfg.levels}
        self.patterns: List[re.Pattern] = [re.compile(pat) for pat in cfg.whitelist_patterns]

    def allow(self, evt: NormalizedEvent) -> bool:
        level = (evt.level or "").upper()
        if level in self.levels:
            return True
        # Whitelist patterns for lower levels (e.g., INFO)
        msg = evt.message or ""
        return any(p.search(msg) for p in self.patterns)
