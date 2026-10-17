#!/usr/bin/env bash
# 在服务器上安装「每日备份」的 crontab（见 ADR 0026）。
#
# 用法（服务器上，root）：
#   bash server-install-cron.sh
#
# 幂等：重复跑只会替换自己装的那一段，不会越装越多。
#
# 时间安排与顺序（改动前先读）：
#   * 03:25 quizzy 库、03:27 MySQL **系统库**——它们与 fit_app 共用同一个实例
#     （`quizzy-mysql`），故障域完全相同，所以一起备。
#     quizzy 用它自己的脚本（那是它仓库管的东西，这里只负责调用、不改它）；
#     系统库用本目录的 `backup-system-mysql.sh`（`mysql` 库里装的是账号与权限，
#     此前三边都没人备）。
#   * 03:30 先给餐照打快照、03:35 再 dump 业务库——**先图片、后数据库**。
#     两者没法在同一瞬间完成，顺序决定「万一卡在中间」的最坏结果：
#     先图后库最坏是多一张没人认领的图片（无害）；反过来会出现
#     「账本上写着有这张照片、翻开相册却没有」（空号，最麻烦）。
#   * 03:50 推 OSS 异地副本，排在本地几份都完成之后。
set -euo pipefail

SCRIPTS_DIR="${SCRIPTS_DIR:-/srv/fit-app/compose/scripts}"
LOG="${CRON_LOG:-/var/log/fit-app-backup.log}"
# quizzy 那边的入口：它自己的仓库与编排，这里只调用
QUIZZY_SCRIPTS_DIR="${QUIZZY_SCRIPTS_DIR:-/srv/quizzy/compose/scripts}"
QUIZZY_ENV="${QUIZZY_ENV:-/srv/quizzy/.env}"

# ⚠️ 这两个标记的文案沿用 "fit-app backup"：一旦改名，重装时 awk 就找不到旧段，
#    旧条目会被留下来变成重复。名字窄一点无所谓，幂等更重要。
BEGIN="# >>> fit-app backup (managed by server-install-cron.sh)"
END="# <<< fit-app backup (managed)"

for f in backup-mysql.sh backup-system-mysql.sh backup-uploads.sh backup-to-oss.sh; do
  if [ ! -x "$SCRIPTS_DIR/$f" ]; then
    echo "❌ $SCRIPTS_DIR/$f 不存在或不可执行——先发一次版本，脚本会随部署送上来" >&2
    exit 1
  fi
done

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

# 先摘掉旧的受管段（幂等的关键）
# ⚠️ 首次安装时 root 还没有 crontab，`crontab -l` 会返回非零（no crontab for root）；
#    在 `set -o pipefail` 之下，这会让整条管道判失败、脚本**静默退出 1**——
#    而「首次安装」恰恰是必经场景。所以必须显式把这个非零吃掉。
(crontab -l 2>/dev/null || true) | awk -v b="$BEGIN" -v e="$END" '
    $0 == b { skip = 1; next }
    $0 == e { skip = 0; next }
    !skip   { print }
' > "$TMP"

{
  cat "$TMP"
  echo "$BEGIN"
  # 先备另外两个库（与 fit-app 同实例）
  echo "25 3 * * * ENV_FILE=$QUIZZY_ENV bash $QUIZZY_SCRIPTS_DIR/backup-mysql.sh >> $LOG 2>&1"
  echo "27 3 * * * cd $SCRIPTS_DIR && ./backup-system-mysql.sh >> $LOG 2>&1"
  # 再备 fit-app：先图片、后数据库
  echo "30 3 * * * cd $SCRIPTS_DIR && ./backup-uploads.sh >> $LOG 2>&1"
  echo "35 3 * * * cd $SCRIPTS_DIR && ./backup-mysql.sh  >> $LOG 2>&1"
  # 最后推异地
  echo "50 3 * * * cd $SCRIPTS_DIR && ./backup-to-oss.sh >> $LOG 2>&1"
  echo "$END"
} | crontab -

echo "✅ 已安装每日备份计划（quizzy / 系统库 / 图片 / fit_app 库 / 异地）："
crontab -l | sed -n "/>>> fit-app backup/,/<<< fit-app backup/p" | sed 's/^/  /'
echo "   日志：$LOG"
