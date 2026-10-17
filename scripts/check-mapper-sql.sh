#!/usr/bin/env bash
# 断言「注解 SQL 里没把 XML 实体用错地方」——一类只在真连库时才炸的机械守卫。
#
#   bash scripts/check-mapper-sql.sh
#
# 为什么要有它：MyBatis 只有在语句被 `<script>` 包裹时才按 XML 解析。
# 不带 `<script>` 的 @Select/@Update/... 会**原样**把字符串发给 MySQL，
# 于是 `&lt;&gt;` 不再是 `<`，而是字面量 `&lt;&gt;` → 语法错误 → 500「系统繁忙」。
# 单测全是 mock，编译也永远绿，这类错只能靠真库发现（2026-10-17 线上就是这么翻的）。
#
# ⚠️ 局限写在明处：它只查「无 <script> 却含实体」这一种形态；
#    有 `<script>` 时实体写没写错（该转义没转）不归它管。
#
# ⚠️ 本文件必须是 LF 换行，CRLF 会让 bash 报 bad interpreter（见 .gitattributes）。
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.."

MAIN_ROOT="backend/src/main/java"
[ -d "$MAIN_ROOT" ] || { echo "找不到 $MAIN_ROOT" >&2; exit 1; }

BAD=0

# 状态机：进 @Select(""" 记下行号，遇 """ 结算；块内含 &lt; / &gt; / &amp;
# 却没有 <script> 的，就是那个坑。
while IFS= read -r hit; do
  [ -n "$hit" ] || continue
  echo "BAD: $hit  —— 注解 SQL 没有 <script> 包裹，实体不会被解析；请写裸的 < > &" >&2
  BAD=$((BAD + 1))
done < <(
  find "$MAIN_ROOT" -name '*.java' -print0 |
    xargs -0 awk '
      FNR == 1 { inblk = 0 }
      !inblk && $0 ~ /@(Select|Update|Insert|Delete)\("""/ {
        inblk = 1; start = FNR; file = FILENAME; buf = ""; next
      }
      inblk {
        if ($0 ~ /"""/) {
          inblk = 0
          hasEntity = (index(buf, "&lt;") || index(buf, "&gt;") || index(buf, "&amp;"))
          if (hasEntity && index(buf, "<script>") == 0) {
            printf "%s:%d\n", file, start
          }
          next
        }
        buf = buf " " $0
      }
    '
)

if [ "$BAD" -gt 0 ]; then
  echo "" >&2
  echo "==> ${BAD} 处注解 SQL 把 HTML 实体写在了不需要转义的位置。" >&2
  exit 1
fi

echo "==> 通过：注解 SQL 的实体使用与 <script> 包裹一致"
