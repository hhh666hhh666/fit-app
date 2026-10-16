#!/usr/bin/env bash
# 在服务器上安装「每日备份」的 crontab（见 ADR 0026）。
#
# 用法（服务器上，root）：
#   bash server-install-cron.sh
#
# 幂等：重复跑只会替换自己装的那一段，不会越装越多。
#
# 时间安排与顺序（改动前先读）：
#   * 03:30 先给餐照打快照、03:35 再 dump 数据库——**先图片、后数据库**。
#     两者没法在同一瞬间完成，顺序决定「万一卡在中间」的最坏结果：
#     先图后库最坏是多一张没人认领的图片（无害）；反过来会出现
#     「账本上写着有这张照片、翻开相册却没有」（空号，最麻烦）。
#   * 03:50 推 OSS 异地副本，排在本地两份都完成之后。
set -euo pipefail

SCRIPTS_DIR="${SCRIPTS_DIR:-/srv/fit-app/compose/scripts}"
LOG="${CRON_LOG:-/var/log/fit-app-backup.log}"

BEGIN="# >>> fit-app backup (managed by server-install-cron.sh)"
END="# <<< fit-app backup (managed)"

if [ ! -x "$SCRIPTS_DIR/backup-mysql.sh" ]; then
  echo "❌ $SCRIPTS_DIR/backup-mysql.sh 不存在或不可执行——先发一次版本，脚本会随部署送上来" >&2
  exit 1
fi

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

# 先摘掉旧的受管段（幂等的关键）
crontab -l 2>/dev/null | awk -v b="$BEGIN" -v e="$END" '
    $0 == b { skip = 1; next }
    $0 == e { skip = 0; next }
    !skip   { print }
' > "$TMP"

{
  cat "$TMP"
  echo "$BEGIN"
  echo "30 3 * * * cd $SCRIPTS_DIR && ./backup-uploads.sh >> $LOG 2>&1"
  echo "35 3 * * * cd $SCRIPTS_DIR && ./backup-mysql.sh  >> $LOG 2>&1"
  echo "50 3 * * * cd $SCRIPTS_DIR && ./backup-to-oss.sh >> $LOG 2>&1"
  echo "$END"
} | crontab -

echo "✅ 已安装每日备份计划："
crontab -l | sed -n "/>>> fit-app backup/,/<<< fit-app backup/p" | sed 's/^/  /'
echo "   日志：$LOG"
