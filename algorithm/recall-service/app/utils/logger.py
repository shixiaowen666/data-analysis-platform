"""
统一日志工具
- 按天入文件夹：LOG_DIR/YYYY-MM-DD/
- 按小时入文件：主日志 HH.log，模块日志 <module>_HH.log
- 超过 LOG_RETENTION_DAYS 天的旧日期文件夹在轮转时自动清理
"""
import logging
import re
import shutil
import sys
from datetime import datetime, timedelta
from pathlib import Path

from app.config import LOG_DIR, LOG_LEVEL, LOG_RETENTION_DAYS

_inited = set()
_DAY_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


class HourlyFileHandler(logging.Handler):
    """写入 LOG_DIR/YYYY-MM-DD/<prefix>HH.log，小时轮转、按天建目录"""

    def __init__(self, module: str = ""):
        super().__init__()
        self.module = module  # 空串 = 主日志
        self._cur_key = None
        self._fh = None
        self._open(force=True)

    def _current_path(self) -> Path:
        now = datetime.now()
        day_dir = LOG_DIR / now.strftime("%Y-%m-%d")
        prefix = f"{self.module}_" if self.module else ""
        return day_dir / f"{prefix}{now.strftime('%H')}.log"

    def _open(self, force: bool = False) -> None:
        key = datetime.now().strftime("%Y%m%d%H")
        if not force and key == self._cur_key and self._fh and not self._fh.closed:
            return
        if self._fh:
            try:
                self._fh.close()
            except OSError:
                pass
        path = self._current_path()
        path.parent.mkdir(parents=True, exist_ok=True)
        self._fh = open(path, "a", encoding="utf-8")
        self._cur_key = key
        self._purge_old()

    def _purge_old(self) -> None:
        if LOG_RETENTION_DAYS <= 0:
            return
        cutoff = (datetime.now() - timedelta(days=LOG_RETENTION_DAYS)).strftime("%Y-%m-%d")
        try:
            for d in LOG_DIR.iterdir():
                if d.is_dir() and _DAY_RE.match(d.name) and d.name < cutoff:
                    shutil.rmtree(d, ignore_errors=True)
        except OSError:
            pass

    def emit(self, record: logging.LogRecord) -> None:
        try:
            self._open()
            self._fh.write(self.format(record) + "\n")
            self._fh.flush()
        except Exception:
            self.handleError(record)

    def close(self) -> None:
        try:
            if self._fh:
                self._fh.close()
        finally:
            super().close()


def get_logger(name: str, file_name: str = None) -> logging.Logger:
    """
    Args:
        name: logger 名称（也作为输出 prefix）
        file_name: 模块日志文件名（如 "api.log"）；文件名主干作为
                   日期文件夹内的小时文件前缀：api -> LOG_DIR/日期/api_HH.log
    """
    logger = logging.getLogger(name)
    if name in _inited:
        return logger

    logger.setLevel(LOG_LEVEL)
    logger.propagate = False

    fmt = logging.Formatter(
        "%(asctime)s | %(levelname)-7s | %(name)-22s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    # 控制台
    console = logging.StreamHandler(sys.stdout)
    console.setFormatter(fmt)
    logger.addHandler(console)

    # 主日志（全模块汇聚，按小时）
    main_h = HourlyFileHandler()
    main_h.setFormatter(fmt)
    logger.addHandler(main_h)

    # 模块专属日志（按小时）
    if file_name:
        mod = Path(file_name).stem
        mod_h = HourlyFileHandler(module=mod)
        mod_h.setFormatter(fmt)
        logger.addHandler(mod_h)

    _inited.add(name)
    return logger


# 各召回路径的专属 logger，便于单独查看
def path_logger(path_name: str) -> logging.Logger:
    """返回某个召回路径专属的 logger（独立日志文件）"""
    file_map = {
        "path_a": "path_a.log",
        "path_b": "path_b.log",
        "path_c": "path_c.log",
        "path_d": "path_d.log",
        "merge": "merge.log",
        "judge": "judge.log",
    }
    return get_logger(f"recall.{path_name}", file_map.get(path_name, f"{path_name}.log"))
