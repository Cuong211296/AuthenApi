import datetime
import json
import re
import sys
from pathlib import Path

import mysql.connector

ROOT = Path(__file__).resolve().parent.parent


def connect():
    env = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip()
    m = re.match(r"jdbc:mysql://([^:/]+)(?::(\d+))?/([^?]+)", env["DB_URL"])
    return mysql.connector.connect(
        host=m.group(1),
        port=int(m.group(2) or 3306),
        database=m.group(3),
        user=env["DB_USERNAME"],
        password=env["DB_PASSWORD"],
    )


def backup():
    conn = connect()
    cur = conn.cursor(dictionary=True)
    cur.execute("SHOW TABLES")
    tables = [list(row.values())[0] for row in cur.fetchall()]
    dump = {}
    for table in tables:
        cur.execute(f"SELECT * FROM `{table}`")
        dump[table] = cur.fetchall()
    out_dir = ROOT / "backups"
    out_dir.mkdir(exist_ok=True)
    out = out_dir / f"backup_{datetime.datetime.now():%Y%m%d_%H%M%S}.json"
    out.write_text(json.dumps(dump, default=str, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Backed up {len(tables)} tables to {out}")


def run(sql_file):
    conn = connect()
    cur = conn.cursor()
    text = Path(sql_file).read_text(encoding="utf-8")
    statements = [s.strip() for s in re.split(r";\s*(?:\r?\n|$)", re.sub(r"--[^\n]*", "", text)) if s.strip()]
    for i, stmt in enumerate(statements, 1):
        print(f"[{i}/{len(statements)}] {stmt.splitlines()[0][:90]}")
        cur.execute(stmt)
        if cur.with_rows:
            print("   ->", cur.fetchall())
    conn.commit()
    print("Done.")


if __name__ == "__main__":
    if sys.argv[1] == "backup":
        backup()
    elif sys.argv[1] == "run":
        run(sys.argv[2])
    else:
        raise SystemExit("usage: dbtool.py backup | run <file.sql>")
