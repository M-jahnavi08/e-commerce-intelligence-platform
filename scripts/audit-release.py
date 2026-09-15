"""Audit release candidates without displaying secret values. Requires Git.

Uses the index plus nonignored files; never stages files. Without a repository,
uses a disposable Git directory to evaluate the project's .gitignore.
This is a targeted check, not a comprehensive secret scanner or history audit.
"""
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def audit(git):
    listing = subprocess.check_output([*git, "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT)
    candidates = sorted(set(listing.decode().split("\0")) - {""})
    local_secrets = []
    if (ROOT / ".env").exists():
        for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
            key, sep, value = line.partition("=")
            if sep and re.search(r"PASSWORD|SECRET|TOKEN", key) and len(value) >= 12:
                local_secrets.append(value.encode())
    findings = []
    generated = {"node_modules", "target", "dist", "work", "outputs", ".venv", "__pycache__", ".pytest_cache"}
    for name in candidates:
        path = ROOT / name
        if not path.is_file():
            continue
        if generated.intersection(Path(name).parts) or (path.name.startswith(".env") and path.name != ".env.example"):
            findings.append((name, "local/generated file in release candidates"))
        data = path.read_bytes()
        if any(value in data for value in local_secrets):
            findings.append((name, "matches a local secret"))
        if re.search(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----", data):
            findings.append((name, "private key material"))
        if re.search(rb"(?:AKIA|ASIA)[A-Z0-9]{16}", data):
            findings.append((name, "AWS access-key identifier"))
    for name, reason in findings:
        print(f"FAIL: {name}: {reason}")
    print(f"Audited {len(candidates)} release candidate files; {len(findings)} findings. No secret values printed.")
    return bool(findings)


if __name__ == "__main__":
    if (ROOT / ".git").exists():
        raise SystemExit(audit(["git"]))
    (ROOT / "work").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="release-audit-", dir=ROOT / "work") as temp:
        subprocess.run(["git", "init", "--quiet", temp], check=True)
        result = audit(["git", "--git-dir=" + str(Path(temp) / ".git"), "--work-tree=" + str(ROOT)])
    raise SystemExit(result)
