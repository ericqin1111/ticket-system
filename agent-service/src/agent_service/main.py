import logging
import os
import time
from .config import AppConfig
from .consumer import LogStreamConsumer


def run():
    log_level = os.getenv("LOG_LEVEL", "INFO").upper()
    logging.basicConfig(level=getattr(logging, log_level, logging.INFO))
    cfg = AppConfig.from_env()
    consumer = LogStreamConsumer(cfg)
    consumer.start()
    try:
        while True:
            consumer.poll_and_process()
    except KeyboardInterrupt:  # pragma: no cover
        pass
    finally:
        consumer.close()


if __name__ == "__main__":
    run()
