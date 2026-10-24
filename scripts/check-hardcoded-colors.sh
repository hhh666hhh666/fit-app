#!/usr/bin/env bash
# 找出前端里「写死的颜色」——设计语言「晴窗」的单一真相源是 `frontend/src/uni.scss`（ADR 0041），
# 页面和组件里出现的色值字面量都是将来换皮时会漏掉的地方。
#
#   bash scripts/check-hardcoded-colors.sh            # 告警模式：列出命中，永远 exit 0
#   bash scripts/check-hardcoded-colors.sh --strict   # 硬失败模式：有命中就 exit 1
#   bash scripts/check-hardcoded-colors.sh -v         # 逐行列出（默认只给每个文件的条数）
#
# 为什么先只告警：页面按批次换皮（见 docs/todo 的设计语言重构待办），地基阶段全站还有几十处
# 旧写法；一上来就硬失败只会逼人加豁免。全部页面换完（第四批）后 CI 改传 --strict。
#
# 查什么：`frontend/src` 下的 .vue / .scss（`uni.scss` 本身除外）里的
#   - 十六进制色值 `#abc` / `#aabbcc` / `#aabbccdd`
#   - `rgb(` / `rgba(` 里直接写数字的（`rgba($token, …)` 这种从 token 派生的不算）
# 不查 .ts：Canvas 画图只能拿字符串色值，那几处要另想办法，不归它管。
#
# ⚠️ 局限写在明处：纯文本匹配，不懂 Vue / SCSS 语法；整行注释（// /* * <!--）会跳过，
#    行尾注释里的色值仍会被数进去。
#
# ⚠️ 本文件必须是 LF 换行，CRLF 会让 bash 报 bad interpreter（见 .gitattributes）。
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.."

STRICT=0
VERBOSE=0
for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=1 ;;
    -v) VERBOSE=1 ;;
    *) echo "未知参数：$arg" >&2; exit 2 ;;
  esac
done

ROOT="frontend/src"
[ -d "$ROOT" ] || { echo "找不到 $ROOT" >&2; exit 1; }

HITS="$(
  find "$ROOT" \( -name '*.vue' -o -name '*.scss' \) ! -path "$ROOT/uni.scss" -print0 |
    xargs -0 grep -nHE '(#[0-9a-fA-F]{8}|#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3})([^0-9a-zA-Z_-]|$)|rgba?\(\s*[0-9]' |
    grep -vE '^[^:]+:[0-9]+:\s*(//|/\*|\*|<!--)' |
    grep -vE '&#[0-9a-fA-F]+;' || true
)"

if [ -z "$HITS" ]; then
  echo "==> 没有写死的颜色"
  exit 0
fi

TOTAL="$(printf '%s\n' "$HITS" | wc -l | tr -d ' ')"

if [ "$VERBOSE" = 1 ]; then
  printf '%s\n' "$HITS"
else
  printf '%s\n' "$HITS" | cut -d: -f1 | sort | uniq -c | sort -rn | sed 's/^/  /'
fi

echo "==> 共 $TOTAL 处写死的颜色：色值请改用 frontend/src/uni.scss 里的 token（ADR 0041）"

if [ "$STRICT" = 1 ]; then
  exit 1
fi
echo "    （告警模式，不阻断；全部页面换皮后 CI 改为 --strict）"
