@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "ENV_FILE=%USERPROFILE%\.workbuddy\ft-local.env"

if not exist "%ENV_FILE%" (
  echo.
  echo �״����У���Ҫ���� MySQL ���root �û�����
  echo ����ֻ������ %ENV_FILE%������д���ֿ⡣
  echo.
  set /p "PW=���������: "
  > "%ENV_FILE%" echo DB_PASSWORD=!PW!
  echo �ѱ��档
)

for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do set "%%a=%%b"

if "%DB_PASSWORD%"=="" (
  echo [����] %ENV_FILE% ��ȱ�� DB_PASSWORD
  if not "%~1"=="silent" pause
  exit /b 1
)

set "SERVER__PORT=8080"
set "DB_USERNAME=root"
set "APP_ADMIN_EMAIL=test@local"
REM 本地开发：开启登录页的「一键登录」按钮（生产环境不要设置）
set "AUTH_QUICK_LOGIN=true"

echo ������������...
call "%~dp0stop.bat" silent

echo ������ˣ��´��ڣ�...
pushd "%~dp0myPhonePro\fund-tracker-backend"
start "��� :8080" cmd /k "mvn spring-boot:run"
popd

echo ����ǰ�ˣ��´��ڣ�...
pushd "%~dp0myPhonePro\stitch_fund_dividend_tracker"
start "ǰ�� :5173" cmd /k "npm run dev"
popd

echo.
echo   ǰ��  http://localhost:5173
echo   ���  http://localhost:8080
echo.
echo �жϳɹ��ķ�����
echo   ��˴��ڳ��� Tomcat started on port 8080 �������ɹ�
echo   ǰ�˴��ڳ��� Local: http://localhost:5173/ �������ɹ�
echo   ���������״�����Լ�� 20-40 �룬����ر�
echo.
if not "%~1"=="silent" pause
