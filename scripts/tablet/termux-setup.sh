#!/data/data/com.termux/files/usr/bin/bash
# One-time setup, run INSIDE Termux on the tablet:  bash ~/tablet-bundle/termux-setup.sh
# Installs Java 17, MariaDB, nginx, ngrok; creates the database and user from the bundle's .env.
# Safe to run again (it skips what is already done).
set -e
cd "$(dirname "$0")"
BUNDLE="$(pwd)"
mkdir -p "$BUNDLE/logs"

echo "== 1/7 The machine"
uname -m
free -m | head -2
ARCH="$(uname -m)"
case "$ARCH" in
  aarch64) NGROK_ARCH=arm64 ;;
  armv7l|armv8l|arm) NGROK_ARCH=arm ;;
  *) echo "Unsupported CPU: $ARCH"; exit 1 ;;
esac

echo "== 2/7 Packages (this takes a while)"
pkg update -y
DEBIAN_FRONTEND=noninteractive apt-get -y -o Dpkg::Options::=--force-confnew upgrade
pkg install -y openjdk-17 mariadb nginx openssh wget curl procps proot resolv-conf termux-tools
java -version

echo "== 3/7 MariaDB (low memory settings)"
mkdir -p "$PREFIX/etc/my.cnf.d"
cat > "$PREFIX/etc/my.cnf.d/tablet.cnf" <<'CNF'
[mysqld]
bind-address=127.0.0.1
skip-name-resolve
performance_schema=OFF
innodb_buffer_pool_size=64M
innodb_log_file_size=16M
key_buffer_size=8M
max_connections=30
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
CNF
DATADIR="$PREFIX/var/lib/mysql"
if [ ! -d "$DATADIR/mysql" ]; then
  if command -v mariadb-install-db >/dev/null 2>&1; then mariadb-install-db --datadir="$DATADIR"; else mysql_install_db; fi
fi
if ! mysqladmin ping --silent 2>/dev/null; then
  nohup mysqld_safe --datadir="$DATADIR" > "$BUNDLE/logs/mariadb.log" 2>&1 &
  for i in $(seq 1 60); do mysqladmin ping --silent 2>/dev/null && break; sleep 2; done
fi
mysqladmin ping --silent || { echo "MariaDB did not start, see logs/mariadb.log"; exit 1; }

echo "== 4/7 Database and user from .env"
DB_USERNAME="$(grep -E '^DB_USERNAME=' .env | head -1 | cut -d= -f2-)"
DB_PASSWORD="$(grep -E '^DB_PASSWORD=' .env | head -1 | cut -d= -f2-)"
[ -n "$DB_USERNAME" ] && [ -n "$DB_PASSWORD" ] || { echo "DB_USERNAME / DB_PASSWORD missing in .env"; exit 1; }
mysql -u root <<SQL
CREATE DATABASE IF NOT EXISTS identity_service CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USERNAME'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON identity_service.* TO '$DB_USERNAME'@'127.0.0.1';
GRANT ALL PRIVILEGES ON identity_service.* TO '$DB_USERNAME'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "== 5/7 nginx (serves web/ on 8080 and forwards /identity to the backend on 8081)"
mkdir -p "$PREFIX/etc/nginx"
[ -f "$PREFIX/etc/nginx/nginx.conf.orig" ] || cp "$PREFIX/etc/nginx/nginx.conf" "$PREFIX/etc/nginx/nginx.conf.orig" 2>/dev/null || true
cat > "$PREFIX/etc/nginx/nginx.conf" <<CONF
worker_processes 1;
events { worker_connections 256; }
http {
    include mime.types;
    default_type application/octet-stream;
    sendfile on;
    client_max_body_size 8m;
    server {
        listen 8080;
        root $BUNDLE/web;
        location /identity/ {
            proxy_pass http://127.0.0.1:8081;
            proxy_set_header Host \$host;
            proxy_set_header X-Forwarded-Proto \$http_x_forwarded_proto;
            # same as the Vite dev proxy: the backend CORS only whitelists localhost origins
            proxy_set_header Origin "";
            proxy_read_timeout 120s;
        }
        location / {
            try_files \$uri /index.html;
        }
    }
}
CONF
nginx -t

echo "== 6/7 ngrok ($NGROK_ARCH)"
if [ ! -x "$PREFIX/bin/ngrok" ]; then
  wget -q -O "$BUNDLE/ngrok.tgz" "https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-$NGROK_ARCH.tgz"
  tar -xzf "$BUNDLE/ngrok.tgz" -C "$PREFIX/bin" ngrok
  rm -f "$BUNDLE/ngrok.tgz"
  chmod +x "$PREFIX/bin/ngrok"
fi
echo "ngrok installed. Add your token once with:  termux-chroot ngrok config add-authtoken <TOKEN>"

echo "== 7/7 Done"
echo "Next: bash $BUNDLE/start-all.sh"
