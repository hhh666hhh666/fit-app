#!/usr/bin/env bash
# 恢复演练：证明「最新那份备份**真的能恢复**」（见 ADR 0026）。
#
# 用法：
#   bash restore-verify.sh              # 用最近一份备份演练
#   bash restore-verify.sh <文件.sql.gz> # 指定某一份
#
# 为什么要这一步：dump 命令返回 0 只说明「它没崩」，不说明「导出来的东西能用」。
# 没验过的备份不是备份，是安慰剂——真出事时才发现恢复不了，那比没有备份更伤人。
# 所以把它挂进发版流水线（每次发版顺手验一份），而不是靠人记得。
#
# 判据（刻意不比对「行数与线上库相等」——备份比当前早，本来就该不同）：
#   ① 导入全过程无错误（set -e + 管道失败可见）；
#   ② 恢复出来的表数量与线上库一致（结构没漂）；
#   ③ 预置数据表 food 非空（dump 若被截断成空壳，这里会兜住）。
set -euo pipefail

SERVER_ROOT="${SERVER_ROOT:-/srv/fit-app}"
ENV_FILE="${ENV_FILE:-$SERVER_ROOT/.env}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-quizzy-mysql}"
BACKUP_DIR="${BACKUP_DIR:-$SERVER_ROOT/backups/db}"
DB="${DB_NAME:-fit_app}"
VERIFY_DB="${VERIFY_DB:-fit_app_verify}"

ROOT_PW=$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' "$ENV_FILE" | head -1)
if [ -z "$ROOT_PW" ]; then
  echo "❌ 读不到 MYSQL_ROOT_PASSWORD（$ENV_FILE）" >&2
  exit 1
fi

DUMP="${1:-}"
if [ -z "$DUMP" ]; then
  DUMP=$(ls -1t "$BACKUP_DIR"/${DB}-*.sql.gz 2>/dev/null | head -1 || true)
fi
if [ -z "$DUMP" ] || [ ! -f "$DUMP" ]; then
  echo "❌ 找不到要演练的备份文件（看 $BACKUP_DIR）" >&2
  exit 1
fi

MY() { docker exec -i -e MYSQL_PWD="$ROOT_PW" "$MYSQL_CONTAINER" mysql -uroot --default-character-set=utf8mb4 "$@"; }

# 临时库用完即删——异常路径也要清掉，别在共享实例上留垃圾
cleanup() { MY -e "DROP DATABASE IF EXISTS \`$VERIFY_DB\`;" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> 演练对象：$DUMP（$(du -h "$DUMP" | cut -f1)）"

cleanup
MY -e "CREATE DATABASE \`$VERIFY_DB\` DEFAULT CHARACTER SET utf8mb4;"

echo "==> 灌入临时库 $VERIFY_DB"
gunzip -c "$DUMP" | MY "$VERIFY_DB"

# 判据 ②：表数量一致
CUR_TABLES=$(MY -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB'")
RES_TABLES=$(MY -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$VERIFY_DB'")
if [ "$CUR_TABLES" != "$RES_TABLES" ]; then
  echo "❌ 表数量不一致：线上库 $CUR_TABLES 张，恢复库 $RES_TABLES 张——dump 可能缺表" >&2
  exit 1
fi
echo "✅ 表数量一致：$RES_TABLES 张"

# 判据 ③：预置食物库非空（它来自迁移种子，必然存在；为空说明 dump 被截断了）
FOOD_ROWS=$(MY -N -e "SELECT COUNT(*) FROM \`$VERIFY_DB\`.food")
if [ "$FOOD_ROWS" -le 0 ]; then
  echo "❌ 恢复库的 food 表为空——dump 很可能被截断了" >&2
  exit 1
fi
echo "✅ 预置食物库非空：$FOOD_ROWS 行"

# 附：各表行数对照。**只展示、不判失败**——备份比当前早，条数本就该不同。
echo "==> 各表行数（线上库 / 恢复库；不相等是正常的，仅供参考）"
for t in $(MY -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB' ORDER BY table_name"); do
  a=$(MY -N -e "SELECT COUNT(*) FROM \`$DB\`.\`$t\`")
  b=$(MY -N -e "SELECT COUNT(*) FROM \`$VERIFY_DB\`.\`$t\`")
  printf "  %-24s %8s / %8s\n" "$t" "$a" "$b"
done

echo "✅ 恢复演练通过：这份备份可以恢复成可用的库"
