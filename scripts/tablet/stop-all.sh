#!/data/data/com.termux/files/usr/bin/bash
# Stops ngrok, nginx, the backend and MariaDB (in that order, so the database closes cleanly last).
pkill -x ngrok 2>/dev/null && echo "ngrok stopped" || echo "ngrok was not running"
nginx -s stop 2>/dev/null && echo "nginx stopped" || echo "nginx was not running"
pkill -f "app.jar" 2>/dev/null && echo "backend stopped" || echo "backend was not running"
sleep 3
mysqladmin shutdown 2>/dev/null && echo "MariaDB stopped" || echo "MariaDB was not running"
termux-wake-unlock 2>/dev/null || true
