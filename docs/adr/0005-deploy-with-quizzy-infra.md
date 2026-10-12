# 部署：独立子域 ＋ 复用 quizzy 的 MySQL 与发布链路

Status: accepted

fit-app 要上公网（ADR 0001：壳 App 加载线上地址，训练现场与外出都要能访问）。部署到**与 quizzy 同一台** 2C2G 轻量服务器：独立子域 `fit.11-2.cn`，宝塔反代到回环 `127.0.0.1:8082`；**数据库复用 quizzy 的 MySQL 容器**（`quizzy-mysql`）里单独的 `fit_app` 库，不再自带一个 MySQL；发布沿用「打 tag → CI 构建推 ACR → SSH 拉镜像重启」这条链路（与 quizzy 同构）。uni-app 换代后缺失的前端镜像（`frontend/Dockerfile` + `nginx.conf`）一并在本轮补齐。

## Considered Options

- **并到路径（如 `11-2.cn/fit`）**：否——与 quizzy 共用站点配置，nginx 规则、证书、日志都要为子路径开口子；独立子域更干净，将来换机器或独立扩展只改 DNS。
- **自带 MySQL 容器**（照抄 quizzy 的编排）：否——这台机器上 `127.0.0.1:3306` 已被 quizzy-mysql 占据，改绑 3307 只是绕开撞车；真正的问题是 2C2G 上再养一份 MySQL（约 160–380MB）挤掉的是 JVM 的余量，而 fit-app 的数据量对 quizzy 那个实例几乎没有增量。
- **在服务器上构建镜像**：否——2C2G 上跑 Maven 必 OOM，构建一律在 CI 上做（compose 里不写 `build:`）。
- **独立子域 ＋ 复用 quizzy MySQL ＋ CI 发布**（选定）。

## Consequences

- **端口**：web 容器绑 `127.0.0.1:8082:80`（8081 已被 quizzy-web 占）；server 不发布端口，只经 web 的 nginx 反代 `/api/`。⚠️ 8082 与安全组/防火墙都**不放行**——反代走机器内部回环。
- **数据库共享**：`docker-compose.prod.yml` 里没有 mysql 服务；server 加入外部网络 `quizzy_default`，直连容器名 `quizzy-mysql`，JDBC URL 带 `createDatabaseIfNotExist=true` 自动建 `fit_app` 库。`.env` 里的 `MYSQL_ROOT_PASSWORD` 必须与 quizzy 的**同值**（同一个实例）。
- **代价（有意接受）**：① 两应用共享故障域与备份范围——quizzy 的 MySQL 挂了 fit-app 一起挂；② quizzy 若 `down/up` 导致网络重建，fit-app 的 server 要跟着重启一次；③ 备份脚本必须 `docker exec` 进 `quizzy-mysql` 去 dump `fit_app`，而不是连 localhost。
- **部署脚本相应改造**：`scripts/server-deploy.sh` 只**检查** `quizzy-mysql` 在跑，绝不上拉、重建或停止别人的容器；它没在跑时直接失败并提示去 `/srv/quizzy` 处理。
- **发布真值**：ACR 与 quizzy 共用实例 `crpi-1e1fwsxsm0ra0pky.cn-chengdu.personal.craliyuncs.com`，命名空间各用各的（`fit-app` / `quizzy`）；`DEPLOY_HOST=8.137.172.6`、`DEPLOY_USER=root`；产物落 `/srv/fit-app`。
- **前端链路**：`frontend/Dockerfile`（node:22-slim 构建 → nginx:alpine）+ `frontend/nginx.conf`（`/api/` 反代到 `server:8080`，resolver 走 Docker 内置 DNS）。⚠️ uni-app 的 H5 产物路径是 `dist/build/h5`，比 vite 多一层。
- 路由是 hash 模式（`src/manifest.json` 的 `h5.router.mode`），宿主反代不需要 SPA 回退；`deploy/nginx/site.conf.example` 里的目标端口同步改成 8082。

> ⚠️ **后续（2026-10-12，部署当天）**：本卡写定时的镜像仓库是阿里云 ACR（与 quizzy 共用一个实例）。
> 首次部署当天，该实例的域名 `crpi-1e1fwsxsm0ra0pky.cn-chengdu.personal.craliyuncs.com`
> 从全球 DNS 消失（阿里／腾讯／Cloudflare／Google 各公共 DNS 均为 NXDOMAIN；GitHub runner
> 在域名消失前约 40 分钟还成功推送过镜像，属「记录被删后缓存仍在」），服务器 `docker pull`
> 报 `no such host`，链路断。**镜像仓库就此换到 ghcr.io**：服务器可达、CI 用内置 `GITHUB_TOKEN`
> 推送（`permissions: packages: write`，不再需要 ACR 的两个 Secrets）、包设 public 后服务器匿名拉取。
> 两个变量顺带改名为中性的 `IMAGE_REGISTRY` / `IMAGE_NAMESPACE`。ACR 实例本身还在，
> 待控制台确认当前地址后再决定是否切回——**其余的部署决策（子域、复用 quizzy MySQL、8082、CI 发布）不变**。
