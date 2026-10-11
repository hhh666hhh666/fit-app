#!/usr/bin/env bash
# 云上部署：拉取镜像仓库里指定版本的镜像，重启 server / web。
# 由发布流水线经 SSH 调用；也可在服务器上手动跑（回滚就是手动跑一个旧版本）。
#
#   bash server-deploy.sh <版本号> [web|server|all]      # 版本号不带 v，如 1.1.0
#
# 从部署脚本继承下来的设计（要改这里之前先读这几条）：
#   * 数据库是**别人的容器**（quizzy-mysql，见 ADR 0005）：只检查它在跑，绝不 up / 重建 / 停止；
#   * up -d 之前先强制备份一次（第一道保险，备份脚本由你自己补，备份对象是 fit_app 库）；
#   * 等 healthcheck（无 healthcheck 的容器退回看 State.Status）；
#   * 失败时打印日志尾部与回滚命令。
#
# ⚠️ 服务器上没有 git，版本号一律从参数拿。
# ⚠️ 必须显式 --env-file：云端 .env 不在 compose 所在目录，而 compose 默认只找自己所在的目录。
# ⚠️ 本文件必须是 LF 换行，CRLF 会让 bash 报 bad interpreter（见 .gitattributes）。
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

COMPOSE_FILE="${COMPOSE_FILE:-../docker-compose.prod.yml}"
SERVER_ROOT="${SERVER_ROOT:-/srv/fit-app}"
ENV_FILE="${ENV_FILE:-$SERVER_ROOT/.env}"
VERSION_FILE="${VERSION_FILE:-$SERVER_ROOT/compose/.current-version}"

RAW_VERSION="${1:-}"
SCOPE="${2:-all}"

# ⚠️ 两组名字别混：compose **服务名**（compose 子命令的参数）与 **容器名**（docker 子命令的参数）。
#    服务名定义在 docker-compose.prod.yml 的 services: 下（server / web），
#    容器名是 container_name（fit-app-server / fit-app-web）。
#    给 compose 传容器名会报 `no such service`；给 docker 传服务名会报 `No such object`。
case "$SCOPE" in
  all)    SERVICES=(server web) ;;
  server) SERVICES=(server) ;;
  web)    SERVICES=(web) ;;
  *)      echo "用法：$0 <版本号，如 1.1.0> [web|server|all]" >&2; exit 1 ;;
esac

if [ -z "$RAW_VERSION" ]; then
  echo "用法：$0 <版本号，如 1.1.0> [web|server|all]" >&2
  echo "服务器上没有 git，版本号必须显式给（CI 传 tag 去掉 v 前缀之后的值）。" >&2
  exit 1
fi

# 镜像 tag 不带 v（git tag 带）。compose 不支持 ${VAR#v} 截断，所以剥前缀只能发生在这里。
APP_VERSION="${RAW_VERSION#v}"
export APP_VERSION

compose() { docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

fail() {
  local reason="$1"; shift
  echo "" >&2
  echo "部署失败：$reason" >&2
  echo "" >&2
  echo "=== 容器日志尾部 ===" >&2
  compose logs --tail=100 "$@" >&2 || true
  echo "" >&2
  echo "=== 怎么回滚 ===" >&2
  local prev=""
  [ -f "$VERSION_FILE" ] && prev="$(cat "$VERSION_FILE")"
  if [ -n "$prev" ]; then
    echo "  回到上一个已上线版本（镜像仍在仓库里，不需要重新构建）：" >&2
    echo "    bash $0 $prev" >&2
  else
    echo "  没有记录到上一个版本（可能是首次部署）。手动给一个仓库里存在的 tag：" >&2
    echo "    bash $0 <旧版本号>" >&2
  fi
  echo "  数据在 quizzy 的 MySQL 实例里（$MYSQL_CONTAINER 的 fit_app 库），不在容器里，不会被卷走。" >&2
  exit 1
}

# 等 healthcheck 跑到 healthy；没有 healthcheck 的容器（web）退回看 State.Status=running。
wait_ready() {
  local name="$1" limit="${2:-180}" elapsed=0 status=""
  while [ "$elapsed" -lt "$limit" ]; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || echo missing)"
    case "$status" in
      healthy|running) echo "    $name 就绪（$status，等了 ${elapsed}s）"; return 0 ;;
      missing)         echo "    $name 不存在" >&2; return 1 ;;
    esac
    sleep 5; elapsed=$((elapsed + 5))
  done
  echo "    等了 ${limit}s，$name 仍是 $status" >&2
  return 1
}

# ---- 1. 前置检查 ----
docker compose version >/dev/null 2>&1 || { echo "docker compose 用不了" >&2; exit 1; }
[ -f "$COMPOSE_FILE" ] || { echo "找不到 $COMPOSE_FILE" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "找不到 $ENV_FILE（云端 .env，模板见仓库 deploy/.env.cloud.example）" >&2; exit 1; }

echo "==> 部署 $APP_VERSION（范围：${SERVICES[*]}）"

# ---- 2. 只确认外部 MySQL 在跑，绝不碰它 ----
# ⚠️ 数据库是 quizzy 的容器（ADR 0005），不归本脚本管：
#    不 up、不 restart、不 down，连 --no-deps 都不需要——compose 里根本没有 mysql 服务。
#    它没在跑时**直接失败**，而不是把别人的容器拉起来（那会顺带影响 quizzy 的可用性）。
MYSQL_CONTAINER="${MYSQL_CONTAINER:-quizzy-mysql}"
if docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
  echo "==> 外部 MySQL $MYSQL_CONTAINER 已在运行"
else
  echo "" >&2
  echo "外部 MySQL 容器 $MYSQL_CONTAINER 没在运行。" >&2
  echo "它是 quizzy 的容器，请去 /srv/quizzy 那边处理（bash /srv/quizzy/compose/scripts/server-compose.sh ps）。" >&2
  echo "本脚本不会替你拉起它。" >&2
  exit 1
fi

# ---- 3. up -d 之前先备份一次（脚本由你自己补；没有就删掉这一步，但要知道代价）----
# ⚠️ 补写时注意：库在**外部容器**里，得 docker exec 进 $MYSQL_CONTAINER 跑 mysqldump fit_app，
#    而不是连 localhost:3306（宿主上 3306 只绑回环、且那是 quizzy 的映射）。
if [ -x ./backup-mysql.sh ]; then
  echo "==> 部署前备份"
  ENV_FILE="$ENV_FILE" MYSQL_CONTAINER="$MYSQL_CONTAINER" bash ./backup-mysql.sh \
    || fail "备份失败，中止部署（不带着没备份的库去跑迁移）"
else
  echo "==> ⚠️ 没有 backup-mysql.sh，跳过部署前备份（建议补上）"
fi

# ---- 4. 拉取新镜像并替换业务容器 ----
# 先 pull 再 up：把「拉不到镜像」这类失败挡在替换之前。
echo "==> 拉取镜像 $APP_VERSION"
compose pull "${SERVICES[@]}" || fail "拉取镜像失败（仓库上有 $APP_VERSION 这个 tag 吗？）" "${SERVICES[@]}"

# --no-deps：不动依赖链（server/web 都在 SERVICES 里显式给出，不需要额外拉起谁）
echo "==> 重建并启动：${SERVICES[*]}"
compose up -d --no-deps "${SERVICES[@]}" || fail "compose up 失败" "${SERVICES[@]}"

# ---- 5. 等健康检查 ----
echo "==> 等待容器就绪"
# wait_ready 走 docker inspect，要用**容器名**；fail 里的 compose logs 用**服务名**
case "$SCOPE" in
  server|all) wait_ready "fit-app-server" 180 || fail "后端未通过健康检查" server ;;
esac
case "$SCOPE" in
  web|all)    wait_ready "fit-app-web" 60    || fail "前端容器没起来" web ;;
esac

# ---- 6. 探一下真实端口（只告警，不中断）----
if command -v curl >/dev/null 2>&1; then
  case "$SCOPE" in
    server|all)
      docker exec "fit-app-server" curl -fsS --max-time 5 -o /dev/null http://127.0.0.1:8080/v3/api-docs \
        && echo "    后端 /v3/api-docs 正常" \
        || echo "    ⚠️ 后端探测失败，看容器日志" ;;
  esac
fi

# ---- 7. 记下已上线版本，供回滚读取 ----
# .current-version 是「现在跑的是谁」；.version-history 是追加的历史，回滚靠它推断「上一个」。
mkdir -p "$(dirname "$VERSION_FILE")"
echo "$APP_VERSION" > "$VERSION_FILE"
echo "$APP_VERSION" >> "${HISTORY_FILE:-$(dirname "$VERSION_FILE")/.version-history}"

echo ""
echo "==> 部署完成，当前状态"
compose ps
echo "已上线版本：$APP_VERSION（记录在 $VERSION_FILE）"
