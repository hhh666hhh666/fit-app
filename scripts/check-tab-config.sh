#!/usr/bin/env bash
# 校验主 tab 的几处手写点没有漂开：
#   1. `frontend/src/pages.json` 的 tabBar.list 与 `frontend/src/utils/tabs.ts` 的 TABS
#      页面路径一致、顺序一致——前者是框架读的（switchTab 只认它），后者是代码读的；
#   2. `frontend/src/utils/nav.ts` 的 SLIDE_MS / SLIDE_EASE 与 `frontend/src/uni.scss` 的
#      $dur-slide / $ease-out 一致——JS 读不到 SCSS 变量，只能镜像一份。
#
#   bash scripts/check-tab-config.sh
#
# ⚠️ 局限写在明处：按行文本提取，依赖这几个文件现在的写法（每个 tab 一行、常量一行）；
#    改了写法提取不到会报 FAIL，而不是静默通过。
#
# ⚠️ 本文件必须是 LF 换行，CRLF 会让 bash 报 bad interpreter（见 .gitattributes）。
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.."

PAGES="frontend/src/pages.json"
TABS="frontend/src/utils/tabs.ts"
NAV="frontend/src/utils/nav.ts"
SCSS="frontend/src/uni.scss"
FAILED=0

fail() { printf '  [FAIL] %s\n' "$1" >&2; FAILED=$((FAILED + 1)); }
ok() { printf '  [ok]   %s\n' "$1"; }

# 1. tab 页面路径与顺序（tr 去掉 CR：Windows 检出时文件可能是 CRLF）
from_pages="$(sed -n '/"tabBar"/,/\]/p' "$PAGES" | tr -d '\r' | grep -o '"pagePath": *"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/')"
from_tabs="$(tr -d '\r' < "$TABS" | grep -o "url: '/[^']*'" | sed "s/url: '\/\([^']*\)'/\1/")"

if [ -z "$from_pages" ] || [ -z "$from_tabs" ]; then
  fail "取不到 tab 列表（pages.json 或 tabs.ts 的写法变了？）"
elif [ "$from_pages" = "$from_tabs" ]; then
  ok "pages.json tabBar 与 tabs.ts TABS 一致：$(echo "$from_tabs" | tr '\n' ' ')"
else
  fail "pages.json tabBar 与 tabs.ts TABS 不一致（路径或顺序）"
  printf '         pages.json: %s\n' "$(echo "$from_pages" | tr '\n' ' ')" >&2
  printf '         tabs.ts:    %s\n' "$(echo "$from_tabs" | tr '\n' ' ')" >&2
fi

# 2. 切页时长与缓动
ms_js="$(tr -d '\r' < "$NAV" | sed -n 's/^export const SLIDE_MS = \([0-9]*\)$/\1/p')"
ms_scss="$(tr -d '\r' < "$SCSS" | sed -n 's/^\$dur-slide: \([0-9]*\)ms;.*/\1/p')"
ease_js="$(tr -d '\r' < "$NAV" | sed -n "s/^export const SLIDE_EASE = '\(.*\)'$/\1/p")"
ease_scss="$(tr -d '\r' < "$SCSS" | sed -n 's/^\$ease-out: \(.*\);$/\1/p')"

if [ -n "$ms_js" ] && [ "$ms_js" = "$ms_scss" ]; then
  ok "SLIDE_MS = \$dur-slide = ${ms_js}ms"
else
  fail "SLIDE_MS（${ms_js:-取不到}）与 \$dur-slide（${ms_scss:-取不到}）不一致"
fi

if [ -n "$ease_js" ] && [ "$ease_js" = "$ease_scss" ]; then
  ok "SLIDE_EASE = \$ease-out = $ease_js"
else
  fail "SLIDE_EASE（${ease_js:-取不到}）与 \$ease-out（${ease_scss:-取不到}）不一致"
fi

if [ "$FAILED" -gt 0 ]; then
  echo "==> $FAILED 处不一致" >&2
  exit 1
fi
echo "==> 通过：主 tab 的手写点一致"
