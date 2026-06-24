@echo off
cd /d "%~dp0myPhonePro\stitch_fund_dividend_tracker"
start "" cmd /c "npm run dev"
echo 前端已启动，按任意键退出...
pause >nul
