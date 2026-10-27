@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "ENV_FILE=%USERPROFILE%\.workbuddy\ft-local.env"

if not exist "%ENV_FILE%" (
  echo.
  echo 首次运行，需要本地 MySQL 口令（root 用户）。
  echo 口令只保存在 %ENV_FILE%，不会写进仓库。
  echo.
  set /p "PW=请输入口令: "
  > "%ENV_FILE%" echo DB_PASSWORD=!PW!
  echo 已保存。
)

for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do set "%%a=%%b"

if "%DB_PASSWORD%"=="" (
  echo [错误] %ENV_FILE% 中缺少 DB_PASSWORD
  if not "%~1"=="silent" pause
  exit /b 1
)

set "SERVER__PORT=8080"
set "DB_USERNAME=root"
set "APP_ADMIN_EMAIL=test@local"

echo 清理残留进程...
call "%~dp0stop.bat" silent

echo 启动后端（新窗口）...
pushd "%~dp0myPhonePro\fund-tracker-backend"
start "后端 :8080" cmd /k "mvn spring-boot:run"
popd

echo 启动前端（新窗口）...
pushd "%~dp0myPhonePro\stitch_fund_dividend_tracker"
start "前端 :5173" cmd /k "npm run dev"
popd

echo.
echo   前端  http://localhost:5173
echo   后端  http://localhost:8080
echo.
echo 判断成功的方法：
echo   后端窗口出现 Tomcat started on port 8080 即启动成功
echo   前端窗口出现 Local: http://localhost:5173/ 即启动成功
echo   两个窗口首次启动约需 20-40 秒，请勿关闭
echo.
if not "%~1"=="silent" pause
