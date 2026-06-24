#!/data/data/com.termux/files/usr/bin/bash
# ==========================================
#  种树 · Termux 一键安装脚本
#  在手机 Termux 中运行此脚本
# ==========================================

echo "===== 1. 更新系统包..."

pkg update -y && pkg upgrade -y

echo "===== 2. 安装 OpenJDK 17 和 MariaDB..."

pkg install -y openjdk-17 mariadb

echo "===== 3. 初始化 MariaDB..."

mysql_install_db --datadir=$PREFIX/var/lib/mysql

echo "===== 4. 启动 MySQL..."

mysqld_safe &
sleep 5

echo "===== 5. 创建数据库..."

mysql -u root <<EOF
CREATE DATABASE IF NOT EXISTS fund_tracker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER USER 'root'@'localhost' IDENTIFIED BY '990428';
FLUSH PRIVILEGES;
EOF

echo "===== 6. 放置程序文件..."

# 将 JAR 放在 home 目录下
cp ~/../usr/home/fund-tracker-backend-1.0.0.jar ~/fund-tracker/

echo "===== 7. 创建启动脚本..."

cat > ~/fund-tracker/start.sh <<'LAUNCH'
#!/data/data/com.termux/files/usr/bin/bash

echo " 种树 · 后台启动中..."

# 启动 MySQL（如未运行）
mysqld_safe &

# 等待 MySQL 就绪
sleep 5

# 启动后端（前台运行，不要关闭终端）
echo "数据库已就绪，启动服务..."
echo "启动后请在手机浏览器访问 http://localhost:8080"
echo "按 Ctrl+C 停止服务"
echo ""

cd ~/fund-tracker
java -jar fund-tracker-backend-1.0.0.jar
LAUNCH
chmod +x ~/fund-tracker/start.sh

echo ""
echo "=============================="
echo "  安装完成！"
echo "=============================="
echo ""
echo "每次使用时："
echo "  1. 打开 Termux"
echo "  2. 输入:  cd ~/fund-tracker && bash start.sh"
echo "  3. 等 30s 后打开浏览器访问 http://localhost:8080"
echo ""
echo "首次使用默认账号: admin / admin123"
echo ""
