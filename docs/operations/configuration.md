# 配置口径

这份文档回答的是**「配置值从哪来、冲突时听谁的、哪些值必须换」**。
具体的变量名与默认值**不在这里复述**——真相源是下面三个文件，本文件只写它们的**优先级与纪律**：

| 位置 | 作用 | 什么时候生效 |
|---|---|---|
| `backend/src/main/resources/application.yml` | 默认值（本机开发兜底） | 最低优先级；`mvn spring-boot:run` 直连本机 3306 时用它 |
| `.env`（由 `.env.example` 复制而来） | 本机 / 服务器真值 | `docker compose` 注入，覆盖上面的默认值 |
| 云主机上的编排文件与其 `.env` | 生产真值 | 只在服务器上有；口径同上 |

**判据**：同一项配置在三处都出现时，**后者覆盖前者**；改了值先去看现在到底生效的是哪一层的。

## 必须在本机完成的一次性动作

```bash
cp .env.example .env    # 然后至少把 JWT 密钥换成随机值（生成命令写在 .env.example 注释里）
```

⚠️ `.env` **不入库**（`.gitignore` 已拦），它是每台机器各填各的。
`docker compose` 会读它，`git` 不会碰它——这就是为什么不把真值写进 `docker-compose.*.yml`。

## 本机 dev 的应用端口：后端 8083、前端 5175

固定约定（2026-10-12 定的），起因是本机 8080 常被 quizzy 的 dev 后端占用，而 8081／8082
是两台容器 web 的端口。落点两处，**改端口必须同时改**：

| 端 | 落点 | 值 |
|---|---|---|
| 后端 | `backend/src/main/resources/application.yml` 的 `server.port` | `${SERVER_PORT:8083}` |
| 前端 | `frontend/vite.config.ts` 的 `server.proxy['/api'].target` | `http://127.0.0.1:8083` |

⚠️ **云上容器内的后端仍是 8080，不要跟着改**：`docker-compose.prod.yml` 注入 `SERVER_PORT: 8080`，
它与 `frontend/nginx.conf` 的反代、compose 的 healthcheck、`scripts/server-deploy.sh` 的探测是一套。
「8083」只约束本机裸跑。

⚠️ 起本机后端前先确认端口归属——8080 上有服务不代表是你的后端（症状：注册写错库、接口 500）。

## 本机端口：3307 而不是 3306

开发机上的 3306 已被**另一个项目的 MySQL 容器**占用，所以 `MYSQL_PORT` 设为 3307，
`application.yml` 的默认连接串跟着走 3307。

**改端口要动两处**：`.env` 的 `MYSQL_PORT`（compose 映射）与 `application.yml` 的默认 URL（本机裸跑）。
两处不一致时，症状是「容器在跑但后端报连接被拒」。

## 本机裸跑的后端读不到 compose 那套环境变量

`mvn spring-boot:run` 只认**自己进程的环境变量**，不会去读 `.env`，所以数据源密码要靠
`SPRING_DATASOURCE_PASSWORD` 单独给一份，且必须与 `MYSQL_ROOT_PASSWORD` 相同。

⚠️ **遗漏它的症状很会骗人**：`docker ps` 显示数据库 healthy、端口也通，
但后端启动报 `Access denied for user 'root'@'172.19.0.1'`——看着像数据库坏了，其实是**密码没传过去**。
（连本机映射端口时，MySQL 看到的来源 IP 是 Docker 网关地址，不是 127.0.0.1，这一样是正常的。）

**本机裸跑的起法（PowerShell）**：先把 `.env` 注入当前进程的环境变量，再启动——

```powershell
Get-Content .env -Encoding UTF8 | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+?)\s*$') {
    Set-Item -Path "Env:\$($matches[1])" -Value $matches[2]
  }
}
& "E:\develop\apache-maven-3.9.9\bin\mvn.cmd" -B -ntp spring-boot:run
```

⚠️ 读 `.env` 必须显式 `-Encoding UTF8`（PowerShell 5.1 默认按 ANSI 读、遇中文注释会静默丢行——丢的正好可能是要用的那行）。
漏 `FITAPP_JWT_SECRET` 的症状是 `PlaceholderResolutionException`（JwtUtil 的 `@Value` 没有默认值）；漏 `SPRING_DATASOURCE_PASSWORD` 的症状是 `Access denied`。

## 密钥这条为什么特殊

- `FITAPP_JWT_SECRET` 必须是**随机值，至少 32 字节**。
- `application.yml` 里那串默认密钥是**随仓库公开的兜底值**，`JwtUtil` 检测到它时会**拒绝使用**，
  改为启动时随机生成（代价是重启后旧 token 失效、需要重新登录）。

所以「忘了配」的后果是**不便，不是安全漏洞**；但正式环境必须在 `.env` 里给真值，
否则每次重启都会把所有人踢下线——那是个容易被误判成「登录有 bug」的现象。

> 环境变量统一前缀 `FITAPP_`，它从项目名 `fit-app` 派生；改项目名则前缀跟着变。

## 云上：数据库不是自己的容器

`docker-compose.prod.yml` 里**没有 mysql 服务**——云上复用 quizzy 的 `quizzy-mysql` 容器，
只用其中的 `fit_app` 库（[ADR 0005](../adr/0005-deploy-with-quizzy-infra.md)）。三处跟着变：

| 项 | 云上 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | 必须与 `/srv/quizzy/.env` 里**同值**——是同一个实例，不是「同名而已」 |
| `MYSQL_DATA_DIR` | 云上不再需要（那是自带 MySQL 时才用的数据卷路径） |
| 库不存在时 | JDBC URL 带 `createDatabaseIfNotExist=true`，首次部署自动建 `fit_app` |

⚠️ 备份脚本**默认从宿主直连 `127.0.0.1:3306`** dump（3306 只绑回环，它映射到 `quizzy-mysql`）；
宿主没有 `mysqldump` 时才退回 `docker exec`。⚠️ 别在容器里连 `localhost`——那是 MySQL 自己；
也别从宿主连 `localhost` 之外的地址，3306 没有对外暴露。2026-10-19 之前走的是容器内 dump，
它在容器被 OOM 时会把备份一起带走（见 `scripts/backup-mysql.sh` 头部注释）。

## 同源部署不需要配 CORS

`fitapp.cors.allowed-origins` 默认为空（= 完全不注册），因为 web 容器与 `/api` 同域。
只有出现**跨域消费者**（独立的移动端域名、第三方页面）时才需要填。
同源却配了 `*` 属于把门拆了，没必要。
