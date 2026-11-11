#!/usr/bin/env bash
# 下载开源动作数据集到 .cache/（不进仓库，ADR 0055）。已下载就跳过；要更新加 --update。
#
#   bash scripts/exercise-dataset/fetch.sh [--update]
#
# 约 160 MB（动图 126 MB），放在仓库目录下，别放 /tmp（有的机器 /tmp 是很小的内存盘）。
# ⚠️ 本文件必须是 LF 换行。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="$HERE/.cache/exercises-dataset"
REPO="https://github.com/hasaneyldrm/exercises-dataset.git"

if [ -d "$DEST/.git" ]; then
  if [ "${1:-}" = "--update" ]; then
    git -C "$DEST" pull --ff-only -q
  fi
else
  mkdir -p "$HERE/.cache"
  git clone -q --depth 1 "$REPO" "$DEST"
fi
echo "✓ 数据集在 $DEST（$(git -C "$DEST" rev-parse --short HEAD)）"
