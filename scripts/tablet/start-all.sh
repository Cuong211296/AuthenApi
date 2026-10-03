#!/data/data/com.termux/files/usr/bin/bash
# Starts MariaDB, the backend, nginx and (if a domain is set) ngrok. Safe to run again.
cd "$(dirname "$0")"
BUNDLE="$(pwd)"
mkdir -p logs

# keeps the CPU awake while the screen is off (also turn off battery optimisation for Termux in Android settings)
termux-wake-lock 2>/dev/null || true

# Java memory: small heap, one cheap GC, no C2 compiler. Raise -Xmx to 400m if `free -m` shows spare memory.
JAVA_OPTS="-Xms64m -Xmx320m -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

if ! mysqladmin ping --silent 2>/dev/null; then
  echo "Starting MariaDB..."
  nohup mysqld_safe --datadir="$PREFIX/var/lib/mysql" > logs/mariadb.log 2>&1 &
  for i in $(seq 1 60); do mysqladmin ping --silent 2>/dev/null && break; sleep 2; done
fi
mysqladmin ping --silent 2>/dev/null && echo "MariaDB: up" || { echo "MariaDB: FAILED (logs/mariadb.log)"; exit 1; }

if ! pgrep -f "app.jar" >/dev/null; then
  echo "Starting backend (first start takes a few minutes on this CPU)..."
  nohup java $JAVA_OPTS -jar app.jar > logs/backend.log 2>&1 &
fi

if ! pgrep -f "nginx: master" >/dev/null; then
  nginx && echo "nginx: up (port 8080)"
fi

DOMAIN="$(grep -E '^FRONTEND_URL=https://' .env | head -1 | cut -d= -f2- | sed 's#^https://##; s#/.*##')"
if [ -n "$DOMAIN" ] && ! pgrep -x ngrok >/dev/null; then
  echo "Starting ngrok for $DOMAIN ..."
  export PROOT_NO_SECCOMP=1   # proot crashes on this old kernel without it
  nohup termux-chroot ngrok http --url="$DOMAIN" 8080 > logs/ngrok.log 2>&1 &
fi

echo "Waiting for the backend (up to 6 minutes)..."
for i in $(seq 1 120); do
  if curl -fs -o /dev/null http://127.0.0.1:8081/identity/products; then
    echo "Backend: up. Open http://127.0.0.1:8080 on the tablet, or http://<tablet-ip>:8080 from another device."
    [ -n "$DOMAIN" ] && echo "Public: https://$DOMAIN"
    exit 0
  fi
  sleep 3
done
echo "Backend did not answer in time. Look at: tail -n 50 $BUNDLE/logs/backend.log"
exit 1
