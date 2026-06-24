@echo off
chcp 65001 >nul
title Fund Tracker Deploy

echo ============================================
echo   Fund Tracker - One Click Deploy
echo ============================================
echo.

cd /d "%~dp0"

echo [1/4] Checking changes...
echo.
git status
echo.

set /p commit_msg=Enter commit message (Enter for auto): 
if "%commit_msg%"=="" set commit_msg=auto deploy %date% %time%

echo.
echo [2/4] Committing...
git add -A
git commit -m "%commit_msg%"
if %errorlevel% neq 0 echo No changes to commit, continuing...

echo.
echo [3/4] Pushing to GitHub...
git push
if %errorlevel% neq 0 (
    echo [ERROR] Push failed! Check your network or GitHub auth.
    pause
    exit /b 1
)
echo Push OK!

echo.
echo [4/4] Deploying on server...
echo.
ssh admin@47.108.64.108 "cd ~ && ./deploy.sh"
if %errorlevel% neq 0 (
    echo [ERROR] Server deploy failed!
    pause
    exit /b 1
)

echo.
echo ============================================
echo   All done! Refresh http://47.108.64.108
echo ============================================
echo.
pause
