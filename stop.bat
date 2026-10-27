@echo off
setlocal enabledelayedexpansion
set "SILENT=%~1"

if not "%SILENT%"=="silent" echo 正在停止前后端...

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do (
  if not "%SILENT%"=="silent" echo   后端 PID %%p
  taskkill /F /PID %%p >nul 2>&1
)

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":5173 " ^| findstr "LISTENING"') do (
  if not "%SILENT%"=="silent" echo   前端 PID %%p
  taskkill /F /PID %%p >nul 2>&1
)

if not "%SILENT%"=="silent" (
  echo.
  echo 已停止。若还有 mvn / node 窗口残留，直接关闭即可。
)
exit /b 0
