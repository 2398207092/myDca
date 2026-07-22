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
sudo aa_nginx -s reload

echo "=== 部署完成 ==="
sudo systemctl status fund-tracker --no-pager | head -5
