#!/usr/bin/env bash
# 给餐照目录打一份**硬链接快照**（见 ADR 0026）。
#
# 用法：
#   bash backup-uploads.sh      # 每日定时调用
#
# 为什么不用 `rsync --delete` 做镜像：镜像只保留「最新状态」，一旦手滑删掉一张，
# 同步会忠实地把删除也照做一遍——备份里那张也跟着没了，没有后悔药。
# 快照每天留一本册子，册子之间**共享没变过的文件**（硬链接），于是：
#   * 能翻回任何一天的样子（恢复到误删之前）；
#   * 每天真正新增的占用 ≈ 当天新拍的那点量，而不是整份相册。
#
# ⚠️ 硬链接不能跨文件系统：快照目录必须与源目录在**同一个挂载点**上，
#    否则 --link-dest 失效、退化成每天全量拷贝（占用放大 KEEP 倍）。脚本会自检并告警。
set -euo pipefail

SERVER_ROOT="${SERVER_ROOT:-/srv/fit-app}"
SRC="${UPLOADS_DIR:-$SERVER_ROOT/uploads}"
BACKUP_ROOT="${UPLOADS_BACKUP_DIR:-$SERVER_ROOT/backups/uploads}"
KEEP="${UPLOADS_BACKUP_KEEP:-30}"
STAMP=$(date +%F)
DEST="$BACKUP_ROOT/$STAMP"

if [ ! -d "$SRC" ]; then
  echo "❌ 源目录不存在：$SRC" >&2
  exit 1
fi

mkdir -p "$BACKUP_ROOT"

# 同盘自检：不同文件系统时长这样会退化成全量拷贝（不报错，但要让人知道）。
if [ "$(stat -c %d "$SRC")" != "$(stat -c %d "$BACKUP_ROOT")" ]; then
  echo "⚠️ $SRC 与 $BACKUP_ROOT 不在同一个文件系统——快照会退化为全量拷贝（占用放大 ${KEEP} 倍）" >&2
fi

if [ -d "$DEST" ]; then
  echo "ℹ️ 今天（$STAMP）的快照已存在，就地刷新"
fi

# 上一册（字典序＝时间序，目录名是 YYYY-MM-DD）
PREV=$(ls -1d "$BACKUP_ROOT"/*/ 2>/dev/null | sort | tail -1 | sed 's:/$::' || true)

if [ -n "$PREV" ] && [ "$PREV" != "$DEST" ]; then
  # --link-dest：未变的文件在新册子里只是硬链接，不占额外空间
  rsync -a --delete --link-dest="$PREV" "$SRC/" "$DEST/"
else
  rsync -a "$SRC/" "$DEST/"
fi

# rsync 会跳过空目录：源为空时「今天这一册」根本不会被创建，保留计数会少一册
mkdir -p "$DEST"

# 保留最近 $KEEP 册（更老的整册删掉；被删册独占的文件随之释放，共享的还在别的册里）
ls -1d "$BACKUP_ROOT"/*/ 2>/dev/null | sort | head -n -"$KEEP" | xargs -r rm -rf

echo "✅ 餐照快照完成：$DEST（源 $(du -sh "$SRC" | cut -f1)，快照合计 $(du -sh "$BACKUP_ROOT" | cut -f1)）"
