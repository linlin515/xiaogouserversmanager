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

    def test_sqlite_store_and_http_endpoints(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "test.db")
            store = agent.MetricsStore(db_path, retention_hours=24)
            collector = agent.AgentCollector(store)
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

                # 3. Snapshot + history
                req_snap = urllib.request.Request(
                    f"http://127.0.0.1:{port}/api/v1/snapshot",
                    headers={"Authorization": "Bearer test-secret"},
                )
                with urllib.request.urlopen(req_snap, timeout=5) as resp:
                    snap = json.loads(resp.read().decode("utf-8"))
                    self.assertIn("cpuPercent", snap)
                    self.assertIn("memTotalBytes", snap)

                req_hist = urllib.request.Request(
                    f"http://127.0.0.1:{port}/api/v1/history?minutes=60",
                    headers={"Authorization": "Bearer test-secret"},
                )
                with urllib.request.urlopen(req_hist, timeout=5) as resp:
                    hist = json.loads(resp.read().decode("utf-8"))
                    self.assertGreaterEqual(len(hist["points"]), 1)
            finally:
                server.shutdown()
                server.server_close()


if __name__ == "__main__":
    unittest.main()
