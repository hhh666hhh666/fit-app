# 部署侧配置模板

这里放**要传到服务器上、但不随镜像走**的东西：宿主反代的站点配置、云端环境变量样例。

**不在这里的东西**：compose 编排（`docker-compose.prod.yml` 在仓库根）、部署脚本（`scripts/server-*.sh`）、
发布流水线（`.github/workflows/release.yml`）——它们由发布流程自动送到服务器，不需要手工传。

## 首次上线前的准备清单

1. **镜像仓库**：建好命名空间，拿到**用户名与固定密码**（不是 AccessKey——个人版镜像仓库通常不支持 AccessKey 推送）。
2. **服务器**：装好容器运行时；确认 80/443 上的对外入口归谁管（若已有面板或反代，就让它反代到本项目的 web 端口，别抢端口）。
3. **仓库 Secrets**：`ACR_USERNAME` / `ACR_PASSWORD` / `DEPLOY_SSH_KEY` / `DEPLOY_SSH_KNOWN_HOSTS`（后两个可选但推荐）。
4. **服务器上的 `.env`**：照 `.env.cloud.example` 填好，放在 `/srv/fit-app/.env`。**这个文件不入库**。
5. **数据库**：云上**不自带 MySQL**——复用同机 quizzy 的 `quizzy-mysql` 容器里的 `fit_app` 库
   （[ADR 0005](../docs/adr/0005-deploy-with-quizzy-infra.md)）。前提：quizzy 已在跑，
   且 `.env` 里的 `MYSQL_ROOT_PASSWORD` 与 `/srv/quizzy/.env` **同值**；`fit_app` 库首次部署自动建。
6. **宿主反代**：web 容器只绑回环 `127.0.0.1:8082`（8081 已被 quizzy-web 占）。
   用面板就加一条反代到 `http://127.0.0.1:8082`；不用面板就照 `nginx/site.conf.example`。
   ⚠️ 安全组与防火墙都**不要**放行 8082——反代走机器内部回环。

## 怎么传

发布流水线每次部署都会把 compose 与脚本 scp 到 `/srv/fit-app/compose/`。
本目录下的两份模板只在**首次**手工传一次：

```bash
scp deploy/.env.cloud.example  <user>@<host>:/srv/fit-app/.env   # 然后改真值
scp deploy/nginx/site.conf.example <user>@<host>:/etc/nginx/conf.d/<site>.conf
```

## 回滚

```bash
cd /srv/fit-app/compose/scripts
bash server-rollback.sh            # 回到上一个已上线版本
bash server-rollback.sh 1.0.3      # 或显式指定
```

⚠️ **回滚只换代码，不回滚数据库**——迁移是不可逆的，所以部署前必须先备份。
