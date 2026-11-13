@echo off
chcp 65001 >nul
setlocal
set "SILENT=%~1"
if not "%SILENT%"=="silent" echo 正在停止 myDca 本地服务（后端 8080 / 前端 5173）...

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='SilentlyContinue'; $self=@($PID); $p=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID); while($p){ $self+=$p.ParentProcessId; $p=Get-CimInstance Win32_Process -Filter ('ProcessId='+$p.ParentProcessId) }; $t=New-Object System.Collections.Generic.List[object]; foreach($port in 8080,5173){ $c=Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue; foreach($x in $c){ $pr=Get-CimInstance Win32_Process -Filter ('ProcessId='+$x.OwningProcess); if($pr){ $t.Add($pr) } } }; $t.AddRange(@(Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and ($_.CommandLine -match 'fund-tracker-backend|spring-boot:run|stitch_fund_dividend_tracker|npm run dev|start\.bat|stop\.bat') })); $uniq=$t | Sort-Object ProcessId -Unique; $killed=0; foreach($x in $uniq){ if($self -notcontains $x.ProcessId){ Stop-Process -Id $x.ProcessId -Force -ErrorAction SilentlyContinue; $killed++ } }; 'killed='+$killed"

if not "%SILENT%"=="silent" (
  echo.
  echo 已停止：后端 8080 / 前端 5173 及相关控制台窗口。
  echo 若仍有残留窗口，直接点右上角关闭即可。
  echo.
  echo 本窗口 3 秒后自动关闭...
  ping -n 4 127.0.0.1 >nul
)
exit /b 0
