#!/usr/bin/env bash
# 把数据集的动图（.gif）与缩略图（.jpg）放进上传目录的 exercise-media/（ADR 0055）。
#
#   bash scripts/exercise-dataset/sync-media.sh <本机目录>            # 本地开发：如 backend/target/dev-uploads
#   bash scripts/exercise-dataset/sync-media.sh aliyun:/srv/fit-app/uploads   # 线上：经 SSH rsync
#
# 文件名就是导入 JSON 里的 media 键加扩展名，应用只读不写。重复跑只补缺、改动的才重传。
# ⚠️ 动图版权归 Gym visual：只放进私有的上传目录，别提交进仓库、别放到公开地址。
# ⚠️ 本文件必须是 LF 换行。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$HERE/.cache/exercises-dataset"
TARGET="${1:?用法：sync-media.sh <上传目录>（本机路径，或 host:/path）}"

[ -d "$SRC/videos" ] && [ -d "$SRC/images" ] || { echo "✗ 没有数据集，先跑 fetch.sh" >&2; exit 1; }

STAGE="$HERE/.cache/exercise-media"
mkdir -p "$STAGE"
# 先在本机拼成目标目录的样子（硬链接，不占空间），再一次 rsync 过去
cp -l -n "$SRC"/videos/*.gif "$STAGE"/ 2>/dev/null || cp -n "$SRC"/videos/*.gif "$STAGE"/
cp -l -n "$SRC"/images/*.jpg "$STAGE"/ 2>/dev/null || cp -n "$SRC"/images/*.jpg "$STAGE"/

if [[ "$TARGET" == *:* ]]; then
  host="${TARGET%%:*}"
  path="${TARGET#*:}"
  ssh "$host" "mkdir -p '$path/exercise-media'"
  rsync -a --chmod=D755,F644 "$STAGE"/ "$host:$path/exercise-media/"
else
  mkdir -p "$TARGET/exercise-media"
  rsync -a --chmod=D755,F644 "$STAGE"/ "$TARGET/exercise-media/"
fi
echo "✓ $(ls "$STAGE" | wc -l) 个文件 → $TARGET/exercise-media/"
