#!/usr/bin/env bash
# 备份 fit_app 库（见 ADR 0026）。
#
# 用法：
#   bash backup-mysql.sh        # 手工跑；每日定时与「部署前钩子」调的都是它
#
# ⚠️ 由 server-deploy.sh 在 up -d 之前调用，那行是
#      `bash ./backup-mysql.sh || fail "备份失败，中止部署"`
#    —— 退出码就是「部署能不能继续」的开关，所以任何异常都必须非零退出。
#
# ⚠️ 库在**别人的容器**里（quizzy-mysql，ADR 0005）：必须 docker exec 进去 dump，
#    不能连 localhost:3306（宿主上那个端口是 quizzy 自己的映射）。
set -euo pipefail

SERVER_ROOT="${SERVER_ROOT:-/srv/fit-app}"
ENV_FILE="${ENV_FILE:-$SERVER_ROOT/.env}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-quizzy-mysql}"
BACKUP_DIR="${BACKUP_DIR:-$SERVER_ROOT/backups/db}"
KEEP="${BACKUP_KEEP:-30}"
DB="${DB_NAME:-fit_app}"

# 口令从 .env 读：① 不进仓库；② 用 MYSQL_PWD 传给客户端而不是 -p 参数——
# 命令行参数会出现在 ps 里，同一个实例上 quizzy 也在跑，没必要让别人看见。
ROOT_PW=$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' "$ENV_FILE" | head -1)
if [ -z "$ROOT_PW" ]; then
  echo "❌ 读不到 MYSQL_ROOT_PASSWORD（$ENV_FILE）" >&2
  exit 1
fi

# 容器必须在跑。只**检查**——绝不上拉/重建/停止别人的容器（ADR 0005）。
if ! docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null | grep -q true; then
  echo "❌ MySQL 容器 $MYSQL_CONTAINER 没在运行，无法备份" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
OUT="$BACKUP_DIR/${DB}-$(date +%Y%m%d-%H%M%S).sql.gz"

# --single-transaction：InnoDB 一致性快照，不锁表（同一个实例上 quizzy 还在读写）
docker exec -e MYSQL_PWD="$ROOT_PW" "$MYSQL_CONTAINER" \
  mysqldump -uroot --single-transaction --default-character-set=utf8mb4 "$DB" \
  | gzip > "$OUT"

# 两道校验。别省：管道里的失败很容易被「命令返回 0」骗过去，
# 而一个 0 字节的「备份」比没有备份更危险——它看起来是成功的。
gzip -t "$OUT"
if [ ! -s "$OUT" ]; then
  echo "❌ 备份文件为空：$OUT" >&2
  exit 1
fi

# 保留最近 $KEEP 份
ls -1t "$BACKUP_DIR"/${DB}-*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f

echo "✅ 库备份完成：$OUT（$(du -h "$OUT" | cut -f1)）"
