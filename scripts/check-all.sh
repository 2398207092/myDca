#!/usr/bin/env bash
# 一键自检：文档 → 接口契约 → UI 冒烟
# 用法：bash scripts/check-all.sh
# 前置：后端 8080（契约与 UI 都需要）、前端 5173（仅 UI 需要）、token 写入 /tmp/token.txt

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
echo

echo "########## 1/3 文档自检 ##########"
if bash scripts/doc-check.sh; then echo "→ 文档 OK"; else echo "→ 文档失败"; fail=1; fi

echo
echo "########## 2/3 接口契约 ##########"
if [ "$be" = "401" ] || [ "$be" = "200" ]; then
  if node scripts/api-contract-check.cjs; then echo "→ 契约 OK"; else echo "→ 契约失败"; fail=1; fi
else
  echo "跳过：后端未启动（8080 无响应）"
  fail=1
fi

echo
echo "########## 3/3 UI 冒烟 ##########"
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
