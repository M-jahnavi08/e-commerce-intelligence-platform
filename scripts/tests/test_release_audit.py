import contextlib
import importlib.util
import io
from pathlib import Path
import secrets
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("release_audit", Path(__file__).resolve().parents[1] / "audit-release.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ReleaseAuditTests(unittest.TestCase):
    def test_detects_secret_without_disclosing_it(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            subprocess.run(["git", "init", "--quiet", temp], check=True)
            (root / ".gitignore").write_text(".env\n")
            secret = secrets.token_urlsafe(24)
            (root / ".env").write_text("JWT_SECRET=" + secret)
            (root / "leaked.txt").write_text(secret)
            output = io.StringIO()
            with patch.object(module, "ROOT", root), contextlib.redirect_stdout(output):
                self.assertTrue(module.audit(["git"]))
            self.assertIn("leaked.txt", output.getvalue())
            self.assertNotIn(secret, output.getvalue())

    def test_ignored_runtime_files_are_not_release_candidates(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            subprocess.run(["git", "init", "--quiet", temp], check=True)
            (root / ".gitignore").write_text(".env\nwork/\n")
            (root / ".env").write_text("JWT_SECRET=" + secrets.token_urlsafe(24))
            (root / "work").mkdir()
            (root / "work" / "backup.txt").write_text("local backup")
            with patch.object(module, "ROOT", root), contextlib.redirect_stdout(io.StringIO()):
                self.assertFalse(module.audit(["git"]))

    def test_tracked_env_is_rejected_even_when_ignored(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            subprocess.run(["git", "init", "--quiet", temp], check=True)
            (root / ".gitignore").write_text(".env\n")
            (root / ".env").write_text("PASSWORD=placeholder")
            subprocess.run(["git", "-C", temp, "add", "--force", ".env"], check=True)
            with patch.object(module, "ROOT", root), contextlib.redirect_stdout(io.StringIO()):
                self.assertTrue(module.audit(["git"]))
