#!/usr/bin/env bash
# 文档自检：死链 + 数字对账
# 用法：bash scripts/doc-check.sh
# 退出码：0 = 全通过；1 = 有硬失败

set -u
cd "$(dirname "$0")/.." || exit 1

fail=0
BE="myPhonePro/fund-tracker-backend/src/main/java/com/fundtracker"

# ---------- 1. 死链 ----------
echo "== 1/2 死链检查 =="
dead=""
while IFS= read -r f; do
  dir="$(dirname "$f")"
  while IFS= read -r link; do
    case "$link" in http*|"") continue ;; esac
    target="${link%%#*}"
    [ -z "$target" ] && continue
    [ -e "$dir/$target" ] && continue
    case "$f" in
      ./docs/archive/*) echo "  WARN  $f -> $link（归档目录，不阻断）" ;;
      *) dead="${dead}  FAIL  $f -> $link"$'\n' ;;
    esac
  done < <(grep -oE '\]\([^)]+\.md(#[^)]*)?\)' "$f" 2>/dev/null | sed -E 's/^\]\(//; s/\)$//')
done < <(find . -name "*.md" -not -path "*/node_modules/*" -not -path "*/target/*" -not -path "*/dist/*" -not -path "./.git/*")

if [ -z "$dead" ]; then
  echo "  OK 无死链"
else
  printf '%s' "$dead"
  fail=1
fi

# ---------- 2. 数字对账 ----------
echo
echo "== 2/2 数字对账（docs/01-系统.md anchors）=="
anchor_line=$(grep -m1 '<!-- anchors:' docs/01-系统.md)
if [ -z "$anchor_line" ]; then
  echo "  FAIL docs/01-系统.md 缺少 anchors 注释行"
  fail=1
else
  check() { # $1=名称 $2=声明值 $3=实际值
    if [ "$2" = "$3" ]; then
      echo "  OK   $1 = $3"
    else
      echo "  FAIL $1 文档写 ${2:-?}，实际 $3"
      fail=1
    fi
  }
  declared() { echo "$anchor_line" | grep -oE "$1=[0-9]+" | cut -d= -f2; }

  check controllers "$(declared controllers)" "$(find "$BE/controller" -maxdepth 1 -name '*.java' | wc -l)"
  check routes      "$(declared routes)"      "$(grep -rhE '@(Get|Post|Put|Delete|Patch)Mapping' "$BE/controller"/*.java | wc -l)"
  check services    "$(declared services)"    "$(find "$BE/service" -maxdepth 1 -name '*.java' | wc -l)"
  check providers   "$(declared providers)"   "$(find "$BE/service/provider" -maxdepth 1 -name '*.java' | wc -l)"
  check entities    "$(declared entities)"    "$(find "$BE/model/entity" -maxdepth 1 -name '*.java' | wc -l)"
  check schedulers  "$(declared schedulers)"  "$(find "$BE/scheduler" -maxdepth 1 -name '*.java' | wc -l)"
fi

echo
[ "$fail" -eq 0 ] && echo "== 通过 ==" || echo "== 有失败项，请修正文档或代码 =="
exit "$fail"
