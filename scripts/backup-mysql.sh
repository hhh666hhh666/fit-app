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
# ⚠️ 库在**别人的容器**里（quizzy-mysql，ADR 0005）。两条取数路径：
#   ① 宿主直连 `127.0.0.1:3306`（quizzy 把端口只绑在回环）——**默认走这条**，
#      dump 进程不计入容器的 cgroup（2026-10-19 容器被 OOM 杀掉后的修正）；
#   ② 宿主没有 mysqldump 时退回 `docker exec`。
#    两条都是**只读**，绝不上拉 / 重建 / 停止别人的容器。
#
# ⚠️ 内存闸：2026-10-19 一次备份把 384MB 的 quizzy-mysql 顶到 OOM，留下 2.8K 的半截文件。
#   现在备份前先看容器余量，低于阈值就**明确失败**（而不是生成一个看着像备份的垃圾），
#   并且先写 `.part`、校验通过才改名——失败时不会在备份目录里留半截文件。
set -euo pipefail

SERVER_ROOT="${SERVER_ROOT:-/srv/fit-app}"
ENV_FILE="${ENV_FILE:-$SERVER_ROOT/.env}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-quizzy-mysql}"
BACKUP_DIR="${BACKUP_DIR:-$SERVER_ROOT/backups/db}"
KEEP="${BACKUP_KEEP:-30}"
DB="${DB_NAME:-fit_app}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
# 容器内存余量阈值（MB）。实测一次 dump 只多 6–7MB，32MB 是「明显异常才挡」的口径。
MIN_HEADROOM_MB="${MIN_HEADROOM_MB:-32}"

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

# 内存闸：只读 cgroup v1 路径，取不到就跳过（别因平台差异挡住备份）。
CGROUP="/sys/fs/cgroup/memory/docker/$(docker inspect -f '{{.Id}}' "$MYSQL_CONTAINER" 2>/dev/null)"
if [ -r "$CGROUP/memory.usage_in_bytes" ] && [ -r "$CGROUP/memory.limit_in_bytes" ]; then
  USAGE=$(cat "$CGROUP/memory.usage_in_bytes")
  LIMIT=$(cat "$CGROUP/memory.limit_in_bytes")
  HEADROOM=$(((LIMIT - USAGE) / 1048576))
  echo "ℹ️ 容器 $MYSQL_CONTAINER 内存余量 ${HEADROOM}MB（阈值 ${MIN_HEADROOM_MB}MB）"
  if [ "$HEADROOM" -lt "$MIN_HEADROOM_MB" ]; then
    echo "❌ 余量不足 ${MIN_HEADROOM_MB}MB（$((USAGE / 1048576))MB / $((LIMIT / 1048576))MB），" \
      "跳过备份以免把共用 MySQL 顶到 OOM" >&2
    exit 1
  fi
fi

mkdir -p "$BACKUP_DIR"
OUT="$BACKUP_DIR/${DB}-$(date +%Y%m%d-%H%M%S).sql.gz"
TMP="$OUT.part"
trap 'rm -f "$TMP"' EXIT

# --single-transaction：InnoDB 一致性快照，不锁表（同一个实例上 quizzy 还在读写）
if command -v mysqldump >/dev/null 2>&1; then
  echo "ℹ️ 用宿主 mysqldump（${MYSQL_HOST}:${MYSQL_PORT}）"
  MYSQL_PWD="$ROOT_PW" mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -uroot \
    --single-transaction --default-character-set=utf8mb4 "$DB" | gzip >"$TMP"
else
  echo "ℹ️ 宿主没有 mysqldump，退回 docker exec"
  docker exec -e MYSQL_PWD="$ROOT_PW" "$MYSQL_CONTAINER" \
    mysqldump -uroot --single-transaction --default-character-set=utf8mb4 "$DB" | gzip >"$TMP"
fi

# 两道校验。别省：管道里的失败很容易被「命令返回 0」骗过去，
# 而一个 0 字节的「备份」比没有备份更危险——它看起来是成功的。
gzip -t "$TMP"
if [ ! -s "$TMP" ]; then
  echo "❌ 备份文件为空：$TMP" >&2
  exit 1
fi
mv "$TMP" "$OUT"
trap - EXIT

# 保留最近 $KEEP 份
ls -1t "$BACKUP_DIR"/${DB}-*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f

echo "✅ 库备份完成：$OUT（$(du -h "$OUT" | cut -f1)）"
