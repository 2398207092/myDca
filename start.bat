@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "ENV_FILE=%USERPROFILE%\.workbuddy\ft-local.env"

rem ---- 检查 MySQL80 服务 ----
sc query MySQL80 2>nul | find "RUNNING" >nul
if errorlevel 1 (
  echo.
  echo [提示] 本机 MySQL80 服务没有运行，后端会启动失败。
  echo        打开「服务」找到 MySQL80 点「启动」；
  echo        或以管理员身份打开命令提示符执行：net start MySQL80
  echo.
)

rem ---- 首次运行：保存数据库密码（只存本机用户目录） ----
if not exist "%ENV_FILE%" (
  echo.
  echo 第一次运行需要本机 MySQL 的 root 密码，只保存在：
  echo   %ENV_FILE%
  echo.
  set /p "PW=请输入数据库密码: "
  > "%ENV_FILE%" echo DB_PASSWORD=!PW!
  echo 已保存。
)

for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do set "%%a=%%b"
if "%DB_PASSWORD%"=="" (
  echo [错误] %ENV_FILE% 缺少 DB_PASSWORD
  if not "%~1"=="silent" pause
  exit /b 1
)

rem ---- 后端本地配置（生产不要照抄） ----
set "SERVER_PORT=8080"
set "DB_USERNAME=root"
set "APP_ADMIN_EMAIL=test@local"
set "AUTH_QUICK_LOGIN=true"

echo 正在停止旧的进程...
call "%~dp0stop.bat" silent

echo 正在启动后端（新窗口）...
pushd "%~dp0myPhonePro\fund-tracker-backend"
start "myDca 后端 :8080" cmd /k "mvn spring-boot:run"
popd

echo 正在启动前端（新窗口）...
pushd "%~dp0myPhonePro\stitch_fund_dividend_tracker"
start "myDca 前端 :5173" cmd /k "npm run dev"
popd

echo 等待服务就绪（最多 90 秒）...
set /a WAITED=0
:WAIT_LOOP
ping -n 3 127.0.0.1 >nul
set /a WAITED+=2
powershell -NoProfile -ExecutionPolicy Bypass -Command "if((Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) -and (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue)){exit 0}else{exit 1}"
if not errorlevel 1 goto :READY
if %WAITED% LSS 90 goto :WAIT_LOOP
echo [提示] 90 秒内没有等到两个端口都就绪，请查看两个新窗口里的日志。
goto :OPEN

:READY
echo 启动完成。

:OPEN
echo.
echo   前端  http://localhost:5173
echo   后端  http://localhost:8080
echo.
start "" http://localhost:5173
echo 已尝试打开浏览器。
echo 要停止服务：双击 stop.bat（会连同两个控制台窗口一起关闭）。
echo.
if not "%~1"=="silent" (
echo 本窗口 5 秒后自动关闭...
ping -n 6 127.0.0.1 >nul
)
exit /b 0
