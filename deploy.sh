#!/bin/bash
# 基金分红追踪器 - 服务器部署脚本
# 用法: ./deploy.sh

set -e

echo "=== 停止旧服务 ==="
sudo systemctl stop fund-tracker 2>/dev/null || true
# 确保旧进程彻底退出（systemctl stop 偶尔杀不干净，会导致端口 8080 占用）
sudo pkill -9 -f fund-tracker.jar 2>/dev/null || true
sleep 2

echo "=== 部署后端 JAR ==="
cp /home/admin/fund-tracker.jar /home/admin/fund-tracker.jar.bak 2>/dev/null || true
# JAR 文件通过 SCP 上传到 /home/admin/fund-tracker.jar

echo "=== 启动后端 ==="
sudo systemctl start fund-tracker

echo "=== 重载 Nginx ==="
# 2026-10-30：aa_nginx -s reload 依赖 /var/run/nginx.pid，该文件可能被系统清理导致部署时报错（不影响已解压的静态文件）。
# 改为按进程号发 HUP（平滑重载，不中断连接）；nginx 未运行时跳过。
NGINX_PID=$(pgrep -o aa_nginx || true)
if [ -n "$NGINX_PID" ]; then sudo kill -HUP "$NGINX_PID"; echo "已平滑重载 nginx (pid=$NGINX_PID)"; else echo "(nginx 未运行，跳过重载)"; fi

echo "=== 部署完成 ==="
sudo systemctl status fund-tracker --no-pager | head -5
