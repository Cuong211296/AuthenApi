import os
import re
import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load_env(path):
    env = {}
    if not path.exists():
        return env
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip().strip('"').strip("'")
    return env


env = {**load_env(ROOT / ".env"), **os.environ}

url = env.get("DB_URL", "jdbc:mysql://localhost:3306/identity_service")
match = re.match(r"jdbc:mysql://([^:/]+)(?::(\d+))?/([^?]+)", url)
if not match:
    raise SystemExit(f"Cannot parse DB_URL: {url}")

host, port, database = match.group(1), match.group(2) or "3306", match.group(3)

os.environ["MYSQL_HOST"] = host
os.environ["MYSQL_PORT"] = port
os.environ["MYSQL_DATABASE"] = database
os.environ["MYSQL_USER"] = env.get("DB_USERNAME", "root")
os.environ["MYSQL_PASSWORD"] = env.get("DB_PASSWORD", "")

runpy.run_module("mysql_mcp_server", run_name="__main__")
