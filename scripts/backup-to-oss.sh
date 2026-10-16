#!/usr/bin/env bash
# 把备份同步到阿里云 OSS（异地副本，见 ADR 0026）。
#
# 用法：
#   bash backup-to-oss.sh
#
# 异地副本防的是「服务器整个没了」——所以它只关心「手上还有没有一整套数据」，
# **不需要留历史版本**（那是本地快照的职责）。由此有两条与本地备份不同的做法：
#   * 图片推的是 `uploads/` 的**当前状态**，刻意**不推** backups/uploads/ 下的快照目录
#     —— OSS 没有硬链接，30 份快照推上去会变成 30 份完整拷贝（占用放大 30 倍）；
#   * 数据库备份本身很小（十几 MB 且图片已出库），整目录推、多留几份无所谓。
#
# 前置：服务器上装好并配置好 ossutil（见 deploy/README.md 的「异地备份」一节），
#       .env 里放 OSS_BUCKET。
set -euo pipefail

SERVER_ROOT="${SERVER_ROOT:-/srv/fit-app}"
ENV_FILE="${ENV_FILE:-$SERVER_ROOT/.env}"
OSSUTIL="${OSSUTIL:-ossutil}"

# 桶名从 .env 读（与口令同一种做法：配置不进仓库）
OSS_BUCKET="${OSS_BUCKET:-$(sed -n 's/^OSS_BUCKET=//p' "$ENV_FILE" 2>/dev/null | head -1)}"
PREFIX="${OSS_PREFIX:-fit-app}"

# 没配桶 ＝ 还没启用异地备份，**优雅跳过**（退出 0），不是失败。
# 这样「先发版、桶以后再说」不会让每天的定时任务在日志里刷错误。
# 反过来说：桶配了却同步不了，那是真问题，下面照常报错退出。
if [ -z "$OSS_BUCKET" ]; then
  echo "ℹ️ 未配置 OSS_BUCKET（$ENV_FILE）——跳过异地同步（配好桶名与 ossutil 后会自动生效）"
  exit 0
fi
if ! command -v "$OSSUTIL" >/dev/null 2>&1; then
  echo "❌ 找不到 ossutil（$OSSUTIL）；安装与配置见 deploy/README.md" >&2
  exit 1
fi

sync_dir() {
  local src="$1" dst="$2"
  if [ ! -d "$src" ]; then
    echo "ℹ️ 跳过（本地不存在）：$src"
    return 0
  fi
  # -u：只传新增与更新的，天然增量；-r 递归
  "$OSSUTIL" cp -r -u "$src" "oss://$OSS_BUCKET/$dst" >/dev/null
  echo "✅ 已同步 $src → oss://$OSS_BUCKET/$dst"
}

sync_dir "$SERVER_ROOT/backups/db" "$PREFIX/db"
sync_dir "$SERVER_ROOT/uploads" "$PREFIX/uploads"

echo "✅ 异地同步完成"
