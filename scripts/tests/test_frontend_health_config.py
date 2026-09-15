"""Keep the Nginx listener, exposed port and IPv4 health probe aligned."""
from pathlib import Path
import re
import unittest
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]

class FrontendHealthConfigurationTests(unittest.TestCase):
    def test_probe_targets_nginx_container_listener(self):
        nginx = (ROOT / "frontend/nginx.conf.template").read_text()
        dockerfile = (ROOT / "frontend/Dockerfile").read_text()
        frontend = (ROOT / "compose.yml").read_text().split("\n  frontend:\n", 1)[1].split("\nvolumes:", 1)[0]
        listen_port = int(re.search(r"listen\s+(\d+)\s*;", nginx).group(1))
        exposed_port = int(re.search(r"^EXPOSE\s+(\d+)", dockerfile, re.M).group(1))
        probe = urlsplit(re.search(r"http://[^\s'\"]+/health", frontend).group(0))
        published = re.search(r"127\.0\.0\.1:(\d+):(\d+)", frontend)
        self.assertEqual(exposed_port, listen_port)
        self.assertEqual(int(published.group(2)), listen_port)
        self.assertEqual(probe.port, listen_port)
        self.assertEqual(probe.hostname, "127.0.0.1", "IPv4-only Nginx must not depend on localhost resolving to IPv4")
        self.assertEqual(probe.path, "/health")
        self.assertRegex(nginx, r"location\s+/health\s*\{[^}]*return\s+200")
        self.assertIn("COPY nginx.conf.template /etc/nginx/templates/default.conf.template", dockerfile)

if __name__ == "__main__":
    unittest.main()
