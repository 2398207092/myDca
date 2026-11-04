#!/usr/bin/env bash
# 一键自检：设计门禁 → 文档 → 前端单测 → 接口契约 → UI 冒烟
# 用法：bash scripts/check-all.sh
# 前置：前端单测需要 node_modules；契约与 UI 需要后端 8080、UI 另需前端 5173；token 自动通过 dev-quick-login 获取（也可用环境变量 TOKEN 传入）

set -u
cd "$(dirname "$0")/.." || exit 1

# UI 检查需要 playwright；自动探测 npx 缓存里的安装路径
if [ -z "${NODE_PATH:-}" ]; then
  PW=$(ls -d "$HOME"/AppData/Local/npm-cache/_npx/*/node_modules 2>/dev/null | head -1)
  [ -n "$PW" ] && export NODE_PATH="$PW"
fi

fail=0
be=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/api/accounts 2>/dev/null)
fe=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:5173 2>/dev/null)

echo "服务状态：后端=$be 前端=$fe"

# token 自动获取：本地后端开着「一键登录」（start.bat 默认开启）时无需手动维护 token 文件
if [ -z "${TOKEN:-}" ]; then
  TOKEN=$(curl -s -H "Content-Type: application/json" -X POST http://localhost:8080/api/auth/dev-quick-login -d '{}' 2>/dev/null | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
  [ -n "$TOKEN" ] && export TOKEN && echo "token：已自动获取（dev-quick-login）"
fi
echo

echo "########## 1/5 设计门禁 ##########"
if node scripts/design-check.cjs; then echo "→ 设计 OK"; else echo "→ 设计失败"; fail=1; fi

echo
echo "########## 2/5 文档自检 ##########"
if bash scripts/doc-check.sh; then echo "→ 文档 OK"; else echo "→ 文档失败"; fail=1; fi

echo
echo "########## 3/5 前端单测 ##########"
if [ -d "myPhonePro/stitch_fund_dividend_tracker/node_modules" ]; then
  if (cd myPhonePro/stitch_fund_dividend_tracker && npm test); then echo "→ 前端单测 OK"; else echo "→ 前端单测失败"; fail=1; fi
else
  echo "失败：前端 node_modules 不存在（先 npm install）"
  fail=1
fi

echo
echo "########## 4/5 接口契约 ##########"
if [ "$be" = "401" ] || [ "$be" = "200" ]; then
  if node scripts/api-contract-check.cjs; then echo "→ 契约 OK"; else echo "→ 契约失败"; fail=1; fi
else
  echo "跳过：后端未启动（8080 无响应）"
  fail=1
fi

echo
echo "########## 5/5 UI 冒烟 ##########"
if [ "$be" = "401" ] || [ "$be" = "200" ]; then
  if [ "$fe" = "200" ]; then
    if node scripts/ui-check.cjs; then echo "→ UI OK"; else echo "→ UI 失败"; fail=1; fi
  else
    echo "跳过：前端未启动（5173 无响应）"
    fail=1
  fi
else
  echo "跳过：后端未启动"
  fail=1
fi

echo
[ "$fail" -eq 0 ] && echo "########## 全部通过 ##########" || echo "########## 有失败项 ##########"
exit "$fail"
