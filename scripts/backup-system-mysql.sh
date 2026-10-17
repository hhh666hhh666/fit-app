#!/usr/bin/env bash
# 备份 MySQL 的**系统库**（`mysql`）——账号与权限都在里面。
#
# 为什么单独成脚本：系统库不是业务数据，但它决定了「实例重建后能不能直接恢复出原来的
# 账号与授权」。此前三个东西都没管它——fit_app 备自己的库、quizzy 备自己的库，
# 而 `mysql` 库（约 7.9MB）谁都没备。
#
# 用法：
#   bash backup-system-mysql.sh
#
# 输出落在与业务库同一个目录（`backups/db/`），文件名 `mysql-<时间戳>.sql.gz`；
# 轮转只认自己这个前缀，不会与 `fit_app-*.sql.gz` 互相干扰。
set -euo pipefail

SERVER_ROOT="${SERVER_ROOT:-/srv/fit-app}"
ENV_FILE="${ENV_FILE:-$SERVER_ROOT/.env}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-quizzy-mysql}"
BACKUP_DIR="${BACKUP_DIR:-$SERVER_ROOT/backups/db}"
KEEP="${BACKUP_KEEP:-30}"
SYS_DB="${SYS_DB:-mysql}"

ROOT_PW=$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' "$ENV_FILE" | head -1)
if [ -z "$ROOT_PW" ]; then
  echo "❌ 读不到 MYSQL_ROOT_PASSWORD（$ENV_FILE）" >&2
  exit 1
fi

if ! docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null | grep -q true; then
  echo "❌ MySQL 容器 $MYSQL_CONTAINER 没在运行，无法备份" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
OUT="$BACKUP_DIR/${SYS_DB}-$(date +%Y%m%d-%H%M%S).sql.gz"

# --single-transaction：系统库里绝大多数是 InnoDB，用它避免锁表（这台机器上 quizzy 还在跑）。
# 不加 --databases：dump 里不带 CREATE DATABASE，恢复时更容易并进别的实例。
docker exec -e MYSQL_PWD="$ROOT_PW" "$MYSQL_CONTAINER" \
  mysqldump -uroot --single-transaction --default-character-set=utf8mb4 "$SYS_DB" \
  | gzip > "$OUT"

# 两道校验：管道里的失败很容易被「命令返回 0」骗过去，而 0 字节的「备份」比没有更危险
gzip -t "$OUT"
if [ ! -s "$OUT" ]; then
  echo "❌ 系统库备份为空：$OUT" >&2
  exit 1
fi

ls -1t "$BACKUP_DIR"/${SYS_DB}-*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f

echo "✅ 系统库备份完成：$OUT（$(du -h "$OUT" | cut -f1)）"
