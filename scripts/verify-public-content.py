"""Reject user-profile paths, machine-specific script defaults, and token literals before publishing."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
paths = subprocess.check_output(
    ["git", "ls-files", "-z"], cwd=root
).decode("utf-8").split("\0")
text_extensions = {".java", ".ps1", ".psm1", ".py", ".md", ".xml", ".properties", ".cmd", ".bat", ".cs", ".c", ".h", ".txt", ".json", ".yaml", ".yml", ".toml", ".sh", ".fxml", ".css", ".html"}
profile = re.compile(r"(?i)(?:[a-z]:[\\/]+Users[\\/]+[^\\/\s\"']+|/(?:home|Users)/[^/\s\"']+)")
drive_literal = re.compile(r"[\"'][A-Za-z]:[\\/]")
token = re.compile(r"(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,}|gputw_live_[A-Za-z0-9]{12,})")
issues = []
count = 0
for name in paths:
    path = root / name
    if not name or not path.is_file() or path.suffix not in text_extensions:
        continue
    try:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
    except UnicodeDecodeError:
        continue
    count += 1
    for number, line in enumerate(lines, 1):
        reason = None
        if profile.search(line):
            reason = "literal user-profile path"
        elif path.suffix in {".ps1", ".py"} and drive_literal.search(line):
            reason = "literal drive path in script; use parameters or environment discovery"
        elif token.search(line):
            reason = "possible credential literal"
        if reason:
            issues.append(f"{name}:{number}: {reason}")
if issues:
    print("\n".join(issues), file=sys.stderr)
    sys.exit(1)
print(f"PASS: {count} tracked text files checked; no matched private paths or token literals.")
print("This is a current-checkout check, not a historical or comprehensive secret audit.")
