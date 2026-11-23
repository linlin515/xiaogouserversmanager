import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(__file__))
import server_eyes_agent as agent


class AgentUnitTest(unittest.TestCase):
    def test_cpu_percent_computation(self):
        self.assertEqual(agent.compute_cpu_percent((1000, 500), (2000, 1000)), 50.0)
        self.assertEqual(agent.compute_cpu_percent(None, (2000, 1000)), 0.0)

    def test_read_docker_stats_graceful_fallback(self):
        stats = agent.read_docker_stats()
        self.assertIsInstance(stats, dict)

    def test_sqlite_store_and_http_endpoints(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "test.db")
            store = agent.MetricsStore(db_path, retention_hours=24)
            collector = agent.AgentCollector(store, snapshot_cache_ttl_sec=5.0, heavy_ttl_sec=30.0)
            handler_cls = agent.make_handler(collector, store, token="test-secret", enable_actions=False)
            server = ThreadingHTTPServer(("127.0.0.1", 0), handler_cls)
            port = server.server_address[1]
            t = threading.Thread(target=server.serve_forever, daemon=True)
            t.start()
            try:
                # 1. Unauthorized rejected
                req_no_auth = urllib.request.Request(f"http://127.0.0.1:{port}/api/v1/health")
                with self.assertRaises(urllib.error.HTTPError) as ctx:
                    urllib.request.urlopen(req_no_auth, timeout=5)
                self.assertEqual(ctx.exception.code, 401)

                # 2. Health check
                req_health = urllib.request.Request(
                    f"http://127.0.0.1:{port}/api/v1/health",
                    headers={"Authorization": "Bearer test-secret"},
                )
                with urllib.request.urlopen(req_health, timeout=5) as resp:
                    self.assertEqual(resp.status, 200)
                    data = json.loads(resp.read().decode("utf-8"))
                    self.assertEqual(data["status"], "ok")

                # 3. Snapshot + cache hit + history
                req_snap = urllib.request.Request(
                    f"http://127.0.0.1:{port}/api/v1/snapshot",
                    headers={"Authorization": "Bearer test-secret"},
                )
                with urllib.request.urlopen(req_snap, timeout=5) as resp:
                    snap1 = json.loads(resp.read().decode("utf-8"))
                    self.assertIn("cpuPercent", snap1)
                    self.assertIn("memTotalBytes", snap1)
                    self.assertIn("diskReadBytesPerSec", snap1)
                    self.assertIn("diskWriteBytesPerSec", snap1)

                # Cached call within TTL returns identical timestamp
                with urllib.request.urlopen(req_snap, timeout=5) as resp:
                    snap2 = json.loads(resp.read().decode("utf-8"))
                    self.assertEqual(snap1["timestampMs"], snap2["timestampMs"])

                req_hist = urllib.request.Request(
                    f"http://127.0.0.1:{port}/api/v1/history?minutes=60",
                    headers={"Authorization": "Bearer test-secret"},
                )
                with urllib.request.urlopen(req_hist, timeout=5) as resp:
                    hist = json.loads(resp.read().decode("utf-8"))
                    self.assertGreaterEqual(len(hist["points"]), 1)

                # 4. Actions disabled by default -> 403
                req_action = urllib.request.Request(
                    f"http://127.0.0.1:{port}/api/v1/action",
                    data=json.dumps({"action": "start", "targetType": "docker", "targetName": "web"}).encode("utf-8"),
                    headers={"Authorization": "Bearer test-secret", "Content-Type": "application/json"},
                    method="POST",
                )
                with self.assertRaises(urllib.error.HTTPError) as ctx_act:
                    urllib.request.urlopen(req_action, timeout=5)
                self.assertEqual(ctx_act.exception.code, 403)
            finally:
                server.shutdown()
                server.server_close()

    def test_empty_token_rejected_by_default_and_rate_limiter_blocks(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "test_sec.db")
            store = agent.MetricsStore(db_path, retention_hours=24)
            collector = agent.AgentCollector(store)
            limiter = agent.AuthRateLimiter(max_failures=3, window_sec=60.0, block_sec=60.0)
            handler_cls = agent.make_handler(
                collector, store, token="", enable_actions=False, allow_unauthenticated=False, rate_limiter=limiter
            )
            server = ThreadingHTTPServer(("127.0.0.1", 0), handler_cls)
            port = server.server_address[1]
            t = threading.Thread(target=server.serve_forever, daemon=True)
            t.start()
            try:
                req = urllib.request.Request(f"http://127.0.0.1:{port}/api/v1/health")
                with self.assertRaises(urllib.error.HTTPError) as ctx:
                    urllib.request.urlopen(req, timeout=5)
                self.assertEqual(ctx.exception.code, 401)
            finally:
                server.shutdown()
                server.server_close()

            # Test rate limiter with valid token and wrong attempts
            limiter2 = agent.AuthRateLimiter(max_failures=2, window_sec=60.0, block_sec=60.0)
            handler_cls2 = agent.make_handler(
                collector, store, token="real-token", enable_actions=False, rate_limiter=limiter2
            )
            server2 = ThreadingHTTPServer(("127.0.0.1", 0), handler_cls2)
            port2 = server2.server_address[1]
            t2 = threading.Thread(target=server2.serve_forever, daemon=True)
            t2.start()
            try:
                for expected in (403, 403, 429):
                    bad_req = urllib.request.Request(
                        f"http://127.0.0.1:{port2}/api/v1/health",
                        headers={"Authorization": "Bearer wrong"},
                    )
                    with self.assertRaises(urllib.error.HTTPError) as ctx:
                        urllib.request.urlopen(bad_req, timeout=5)
                    self.assertEqual(ctx.exception.code, expected)
            finally:
                server2.shutdown()
                server2.server_close()


if __name__ == "__main__":
    unittest.main()
