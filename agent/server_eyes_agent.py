#!/usr/bin/env python3
"""
XiaoGou Servers Eyes - Optional Lightweight Server Agent (Zero third-party dependencies).
Exposes Bearer-token authenticated HTTP API + SQLite historical ring-buffer.
"""

from __future__ import annotations

import argparse
import hmac
import json
import os
import platform
import re
import socket
import sqlite3
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import parse_qs, urlparse

VERSION = "1.0.0"
SAFE_TARGET_RE = re.compile(r"^[A-Za-z0-9_.:@-]{1,128}$")
IGNORED_FS = {"tmpfs", "devtmpfs", "udev", "overlay", "squashfs", "efivarfs"}


def run_cmd(cmd: List[str], timeout: float = 5.0) -> Tuple[int, str]:
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout,
            check=False,
        )
        return proc.returncode, proc.stdout
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError) as exc:
        return 1, str(exc)


def read_proc_stat() -> Optional[Tuple[int, int]]:
    try:
        with open("/proc/stat", "r", encoding="utf-8") as fh:
            line = fh.readline().strip()
        if not line.startswith("cpu "):
            return None
        nums = [int(x) for x in line.split()[1:9]]
        while len(nums) < 8:
            nums.append(0)
        total = sum(nums)
        idle = nums[3] + nums[4]
        return total, idle
    except OSError:
        return None


def compute_cpu_percent(prev: Optional[Tuple[int, int]], curr: Optional[Tuple[int, int]]) -> float:
    if not prev or not curr:
        return 0.0
    total_delta = curr[0] - prev[0]
    idle_delta = curr[1] - prev[1]
    if total_delta <= 0:
        return 0.0
    busy = max(0, total_delta - idle_delta)
    return round(min(100.0, max(0.0, (busy / total_delta) * 100.0)), 2)


def read_meminfo() -> Dict[str, int]:
    values_kb: Dict[str, int] = {}
    try:
        with open("/proc/meminfo", "r", encoding="utf-8") as fh:
            for line in fh:
                if ":" not in line:
                    continue
                k, rest = line.split(":", 1)
                parts = rest.strip().split()
                if parts and parts[0].isdigit():
                    values_kb[k.strip()] = int(parts[0])
    except OSError:
        return {"memTotalBytes": 0, "memUsedBytes": 0, "swapTotalBytes": 0, "swapUsedBytes": 0}

    total_kb = values_kb.get("MemTotal", 0)
    avail_kb = values_kb.get(
        "MemAvailable",
        values_kb.get("MemFree", 0)
        + values_kb.get("Buffers", 0)
        + values_kb.get("Cached", 0)
        + values_kb.get("SReclaimable", 0),
    )
    used_kb = max(0, total_kb - avail_kb)
    swap_total_kb = values_kb.get("SwapTotal", 0)
    swap_free_kb = values_kb.get("SwapFree", 0)
    swap_used_kb = max(0, swap_total_kb - swap_free_kb)
    return {
        "memTotalBytes": total_kb * 1024,
        "memUsedBytes": used_kb * 1024,
        "swapTotalBytes": swap_total_kb * 1024,
        "swapUsedBytes": swap_used_kb * 1024,
    }


def read_net_bytes() -> Tuple[int, int]:
    rx_total = 0
    tx_total = 0
    try:
        with open("/proc/net/dev", "r", encoding="utf-8") as fh:
            for line in fh:
                if ":" not in line:
                    continue
                iface, rest = line.split(":", 1)
                iface = iface.strip()
                if iface == "lo" or iface.startswith(("veth", "br-")) or iface == "docker0":
                    continue
                cols = rest.split()
                if len(cols) >= 9:
                    rx_total += int(cols[0])
                    tx_total += int(cols[8])
    except OSError:
        pass
    return rx_total, tx_total


def read_uptime_and_load() -> Tuple[int, float, float, float]:
    uptime_s = 0
    load1 = load5 = load15 = 0.0
    try:
        with open("/proc/uptime", "r", encoding="utf-8") as fh:
            uptime_s = int(float(fh.read().split()[0]))
    except (OSError, ValueError, IndexError):
        pass
    try:
        with open("/proc/loadavg", "r", encoding="utf-8") as fh:
            parts = fh.read().split()
            load1, load5, load15 = float(parts[0]), float(parts[1]), float(parts[2])
    except (OSError, ValueError, IndexError):
        pass
    return uptime_s, load1, load5, load15


def read_os_pretty_name() -> str:
    try:
        with open("/etc/os-release", "r", encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("PRETTY_NAME="):
                    return line.split("=", 1)[1].strip().strip('"')
    except OSError:
        pass
    return f"{platform.system()} {platform.release()}"


def read_disks() -> List[Dict[str, Any]]:
    rc, out = run_cmd(["df", "-kP"], timeout=4.0)
    if rc != 0:
        return []
    seen = set()
    result = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) < 6:
            continue
        fs = parts[0]
        mount = " ".join(parts[5:])
        if fs in IGNORED_FS or fs.startswith("none"):
            continue
        if mount.startswith(("/snap", "/sys", "/proc")) or mount in seen:
            continue
        seen.add(mount)
        try:
            total_kb = int(parts[1])
            used_kb = int(parts[2])
            avail_kb = int(parts[3])
            pct = float(parts[4].rstrip("%"))
        except ValueError:
            continue
        result.append(
            {
                "filesystem": fs,
                "mountPoint": mount,
                "totalBytes": total_kb * 1024,
                "usedBytes": used_kb * 1024,
                "availBytes": avail_kb * 1024,
                "usagePercent": min(100.0, max(0.0, pct)),
            }
        )
    return result


def read_top_processes() -> List[Dict[str, Any]]:
    rc, out = run_cmd(["ps", "-eo", "pid,comm,%cpu,%mem", "--sort=-%cpu"], timeout=4.0)
    if rc != 0:
        return []
    procs = []
    for line in out.splitlines()[1:11]:
        parts = line.split(None, 3)
        if len(parts) < 4:
            continue
        try:
            procs.append(
                {
                    "pid": int(parts[0]),
                    "name": parts[1],
                    "cpuPercent": float(parts[2]),
                    "memPercent": float(parts[3]),
                }
            )
        except ValueError:
            continue
    return procs


def read_docker_containers() -> List[Dict[str, Any]]:
    rc, out = run_cmd(
        ["docker", "ps", "-a", "--no-trunc", "--format", "{{.ID}}|{{.Names}}|{{.Image}}|{{.State}}|{{.Status}}|{{.Ports}}"],
        timeout=5.0,
    )
    if rc != 0:
        return []
    items = []
    for line in out.splitlines():
        parts = line.split("|")
        if len(parts) < 5:
            continue
        items.append(
            {
                "id": parts[0][:12],
                "name": parts[1],
                "image": parts[2],
                "state": parts[3],
                "statusText": parts[4],
                "ports": parts[5] if len(parts) > 5 else "",
            }
        )
    return items


def read_systemd_services() -> List[Dict[str, Any]]:
    rc, out = run_cmd(
        ["systemctl", "list-units", "--type=service", "--all", "--no-pager", "--no-legend"],
        timeout=5.0,
    )
    if rc != 0:
        return []
    items = []
    for line in out.splitlines()[:60]:
        cleaned = line.lstrip("● ").strip()
        parts = cleaned.split(None, 4)
        if len(parts) < 4 or not parts[0].endswith(".service"):
            continue
        items.append(
            {
                "unit": parts[0],
                "loadState": parts[1],
                "activeState": parts[2],
                "subState": parts[3],
                "description": parts[4] if len(parts) > 4 else "",
            }
        )
    return items


def read_listening_ports() -> List[Dict[str, Any]]:
    rc, out = run_cmd(["ss", "-tulnH"], timeout=4.0)
    if rc != 0:
        return []
    seen = set()
    ports = []
    for line in out.splitlines()[:50]:
        parts = line.split()
        if len(parts) < 5:
            continue
        proto = parts[0]
        local = parts[4]
        if ":" not in local:
            continue
        addr, port_str = local.rsplit(":", 1)
        addr = addr.strip("[]")
        if not port_str.isdigit():
            continue
        port = int(port_str)
        key = (proto, addr, port)
        if key in seen:
            continue
        seen.add(key)
        ports.append({"protocol": proto, "localAddress": addr, "port": port, "processName": ""})
    return ports


class MetricsStore:
    def __init__(self, db_path: str, retention_hours: int = 48) -> None:
        self.db_path = db_path
        self.retention_ms = max(1, retention_hours) * 3600 * 1000
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(db_path, check_same_thread=False)
        self._conn.execute(
            """
            CREATE TABLE IF NOT EXISTS history (
                timestamp_ms INTEGER PRIMARY KEY,
                cpu_percent REAL NOT NULL,
                mem_percent REAL NOT NULL,
                net_rx_bps INTEGER NOT NULL,
                net_tx_bps INTEGER NOT NULL,
                load1 REAL NOT NULL
            )
            """
        )
        self._conn.commit()

    def record(self, snapshot: Dict[str, Any]) -> None:
        mem_total = snapshot.get("memTotalBytes", 0)
        mem_used = snapshot.get("memUsedBytes", 0)
        mem_pct = round((mem_used / mem_total) * 100.0, 2) if mem_total > 0 else 0.0
        ts = int(snapshot["timestampMs"])
        with self._lock:
            self._conn.execute(
                "INSERT OR REPLACE INTO history VALUES (?, ?, ?, ?, ?, ?)",
                (
                    ts,
                    float(snapshot["cpuPercent"]),
                    mem_pct,
                    int(snapshot["netRxBytesPerSec"]),
                    int(snapshot["netTxBytesPerSec"]),
                    float(snapshot["load1"]),
                ),
            )
            cutoff = ts - self.retention_ms
            self._conn.execute("DELETE FROM history WHERE timestamp_ms < ?", (cutoff,))
            self._conn.commit()

    def query(self, minutes: int) -> List[Dict[str, Any]]:
        since = int(time.time() * 1000) - max(1, minutes) * 60 * 1000
        with self._lock:
            rows = self._conn.execute(
                "SELECT timestamp_ms, cpu_percent, mem_percent, net_rx_bps, net_tx_bps, load1 "
                "FROM history WHERE timestamp_ms >= ? ORDER BY timestamp_ms ASC",
                (since,),
            ).fetchall()
        return [
            {
                "timestampMs": r[0],
                "cpuPercent": r[1],
                "memPercent": r[2],
                "netRxBytesPerSec": r[3],
                "netTxBytesPerSec": r[4],
                "load1": r[5],
            }
            for r in rows
        ]


class AgentCollector:
    def __init__(self, store: MetricsStore) -> None:
        self.store = store
        self._lock = threading.Lock()
        self._prev_cpu: Optional[Tuple[int, int]] = read_proc_stat()
        self._prev_net: Optional[Tuple[int, int, float]] = None
        rx, tx = read_net_bytes()
        self._prev_net = (rx, tx, time.time())
        self._latest_snapshot: Optional[Dict[str, Any]] = None

    def collect_once(self, include_heavy: bool = True) -> Dict[str, Any]:
        with self._lock:
            now_s = time.time()
            now_ms = int(now_s * 1000)
            curr_cpu = read_proc_stat()
            cpu_pct = compute_cpu_percent(self._prev_cpu, curr_cpu)
            self._prev_cpu = curr_cpu

            rx, tx = read_net_bytes()
            rx_bps = tx_bps = 0
            if self._prev_net is not None:
                dt = max(0.001, now_s - self._prev_net[2])
                rx_bps = int(max(0, rx - self._prev_net[0]) / dt)
                tx_bps = int(max(0, tx - self._prev_net[1]) / dt)
            self._prev_net = (rx, tx, now_s)

            uptime_s, load1, load5, load15 = read_uptime_and_load()
            mem = read_meminfo()

            snapshot: Dict[str, Any] = {
                "timestampMs": now_ms,
                "hostname": socket.gethostname(),
                "osName": read_os_pretty_name(),
                "kernel": f"{platform.system()} {platform.release()}",
                "uptimeSeconds": uptime_s,
                "cpuCores": os.cpu_count() or 1,
                "cpuPercent": cpu_pct,
                "load1": load1,
                "load5": load5,
                "load15": load15,
                **mem,
                "netRxBytesPerSec": rx_bps,
                "netTxBytesPerSec": tx_bps,
            }
            if include_heavy or self._latest_snapshot is None:
                snapshot["disks"] = read_disks()
                snapshot["topProcesses"] = read_top_processes()
                snapshot["containers"] = read_docker_containers()
                snapshot["services"] = read_systemd_services()
                snapshot["ports"] = read_listening_ports()
            else:
                for k in ("disks", "topProcesses", "containers", "services", "ports"):
                    snapshot[k] = self._latest_snapshot.get(k, [])

            self._latest_snapshot = snapshot
            self.store.record(snapshot)
            return snapshot

    def get_snapshot(self) -> Dict[str, Any]:
        if self._latest_snapshot is None:
            return self.collect_once(include_heavy=True)
        return self.collect_once(include_heavy=True)


def make_handler(collector: AgentCollector, store: MetricsStore, token: str, enable_actions: bool):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, format: str, *args: Any) -> None:
            return

        def _send_json(self, status: int, payload: Dict[str, Any]) -> None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _authorized(self) -> bool:
            if not token:
                return True
            auth = self.headers.get("Authorization", "")
            if not auth.startswith("Bearer "):
                self._send_json(401, {"error": "unauthorized"})
                return False
            supplied = auth[7:].strip()
            if not hmac.compare_digest(supplied, token):
                self._send_json(403, {"error": "forbidden"})
                return False
            return True

        def do_GET(self) -> None:
            if not self._authorized():
                return
            parsed = urlparse(self.path)
            qs = parse_qs(parsed.query)

            if parsed.path == "/api/v1/health":
                self._send_json(
                    200,
                    {
                        "status": "ok",
                        "version": VERSION,
                        "hostname": socket.gethostname(),
                        "osName": read_os_pretty_name(),
                    },
                )
            elif parsed.path == "/api/v1/snapshot":
                self._send_json(200, collector.get_snapshot())
            elif parsed.path == "/api/v1/history":
                minutes = int(qs.get("minutes", ["60"])[0])
                self._send_json(200, {"points": store.query(minutes)})
            elif parsed.path == "/api/v1/logs":
                target_type = qs.get("type", ["journal"])[0]
                target = qs.get("target", [""])[0]
                lines = max(10, min(500, int(qs.get("lines", ["120"])[0])))
                if target_type in ("docker", "systemd") and not SAFE_TARGET_RE.match(target):
                    self._send_json(400, {"error": "invalid target"})
                    return
                if target_type == "docker":
                    _, out = run_cmd(["docker", "logs", "--tail", str(lines), target], timeout=10.0)
                elif target_type == "systemd":
                    _, out = run_cmd(["journalctl", "-u", target, "-n", str(lines), "--no-pager"], timeout=10.0)
                else:
                    _, out = run_cmd(["journalctl", "-n", str(lines), "--no-pager"], timeout=10.0)
                self._send_json(200, {"lines": out.splitlines()})
            else:
                self._send_json(404, {"error": "not found"})

        def do_POST(self) -> None:
            if not self._authorized():
                return
            parsed = urlparse(self.path)
            if parsed.path != "/api/v1/action":
                self._send_json(404, {"error": "not found"})
                return
            if not enable_actions:
                self._send_json(403, {"ok": False, "message": "Agent 未启用远程运维动作 (--enable-actions)"})
                return
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(min(length, 16384))
            try:
                data = json.loads(raw.decode("utf-8"))
            except json.JSONDecodeError:
                self._send_json(400, {"ok": False, "message": "invalid json"})
                return
            action = data.get("action", "")
            target_type = data.get("targetType", "")
            target_name = data.get("targetName", "")
            if action not in ("start", "stop", "restart") or not SAFE_TARGET_RE.match(target_name):
                self._send_json(400, {"ok": False, "message": "不支持的动作或非法目标名"})
                return
            if target_type == "docker":
                rc, out = run_cmd(["docker", action, target_name], timeout=20.0)
            elif target_type == "systemd":
                rc, out = run_cmd(["systemctl", action, target_name], timeout=20.0)
            else:
                self._send_json(400, {"ok": False, "message": "未知目标类型"})
                return
            self._send_json(200 if rc == 0 else 500, {"ok": rc == 0, "message": out.strip() or f"已执行 {action} {target_name}"})

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description="XiaoGou Servers Eyes Lightweight Agent")
    parser.add_argument("--host", default="0.0.0.0", help="Listen host (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=19090, help="Listen port (default: 19090)")
    parser.add_argument("--token", default=os.environ.get("SERVER_EYES_TOKEN", ""), help="Bearer auth token")
    parser.add_argument("--interval", type=int, default=10, help="Background sample interval seconds")
    parser.add_argument("--db-path", default="server_eyes_agent.db", help="SQLite ring-buffer path")
    parser.add_argument("--retention-hours", type=int, default=48, help="History retention hours")
    parser.add_argument("--enable-actions", action="store_true", help="Allow container/service restart via API")
    args = parser.parse_args()

    store = MetricsStore(args.db_path, args.retention_hours)
    collector = AgentCollector(store)

    def sampler_loop() -> None:
        while True:
            try:
                collector.collect_once(include_heavy=False)
            except Exception:
                pass
            time.sleep(max(3, args.interval))

    threading.Thread(target=sampler_loop, daemon=True).start()
    server = ThreadingHTTPServer(
        (args.host, args.port),
        make_handler(collector, store, args.token, args.enable_actions),
    )
    print(f"XiaoGou ServerEyes Agent v{VERSION} listening on {args.host}:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.shutdown()


if __name__ == "__main__":
    main()
