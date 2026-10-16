# 部署侧配置模板

这里放**要传到服务器上、但不随镜像走**的东西：宿主反代的站点配置、云端环境变量样例。

**不在这里的东西**：compose 编排（`docker-compose.prod.yml` 在仓库根）、部署脚本（`scripts/server-*.sh`）、
发布流水线（`.github/workflows/release.yml`）——它们由发布流程自动送到服务器，不需要手工传。

## 首次上线前的准备清单

1. **镜像仓库 = ghcr.io**：无需任何配置——CI 用内置 `GITHUB_TOKEN` 推送（workflow 已声明 `packages: write`）。
   首次推送后，把两个包（fit-app-server / fit-app-web）的可见性改成 **public**，服务器才能匿名拉取。
2. **服务器**：装好容器运行时；确认 80/443 上的对外入口归谁管（若已有面板或反代，就让它反代到本项目的 web 端口，别抢端口）。
3. **仓库 Secrets**：`DEPLOY_SSH_KEY` / `DEPLOY_SSH_KNOWN_HOSTS`（后者可选，不配则 CI 现场 ssh-keyscan）。
4. **服务器上的 `.env`**：照 `.env.cloud.example` 填好，放在 `/srv/fit-app/.env`。**这个文件不入库**。
5. **数据库**：云上**不自带 MySQL**——复用同机 quizzy 的 `quizzy-mysql` 容器里的 `fit_app` 库
   （[ADR 0005](../docs/adr/0005-deploy-with-quizzy-infra.md)）。前提：quizzy 已在跑，
   且 `.env` 里的 `MYSQL_ROOT_PASSWORD` 与 `/srv/quizzy/.env` **同值**；`fit_app` 库首次部署自动建。
6. **宿主反代**：web 容器只绑回环 `127.0.0.1:8082`（8081 已被 quizzy-web 占）。
   用面板就加一条反代到 `http://127.0.0.1:8082`；不用面板就照 `nginx/site.conf.example`。
   ⚠️ 安全组与防火墙都**不要**放行 8082——反代走机器内部回环。
7. **图片目录**：餐照实体存在服务器文件系统上（[ADR 0026](../docs/adr/0026-photo-storage-and-backup.md)），
   compose 已把 `/srv/fit-app/uploads` 绑定到容器的 `/data/uploads`。
   ⚠️ 这个目录**必须持久**——它不随容器重建消失，但也绝不能写成容器里的可写层路径
   （那会导致「库还在、照片全没」）。建议先手工 `mkdir -p /srv/fit-app/uploads`，
   免得 Docker 自动创建时属主不对。备份目录 `/srv/fit-app/backups/` 与它分开，别放在 uploads 里面。
8. **异地备份（OSS）**：备份会推一份到对象存储（同地域走内网，不占公网带宽、不计流量费）。
   服务器上装并配置 ossutil，凭据只存在于服务器、**不进仓库**：
   ```bash
   curl -fsSL -o /usr/local/bin/ossutil \
     https://gosspublic.alicdn.com/ossutil/1.7.19/ossutil64 && chmod 755 /usr/local/bin/ossutil
   ossutil config -e oss-cn-<地域>-internal.aliyuncs.com -i <AccessKeyId> -k <AccessKeySecret>
   ```
   然后在 `/srv/fit-app/.env` 里加 `OSS_BUCKET=<桶名>`。**桶建成私有的**——里面有训练记录与餐照。

## 怎么传

发布流水线每次部署都会把 compose 与脚本 scp 到 `/srv/fit-app/compose/`。
本目录下的两份模板只在**首次**手工传一次：

```bash
scp deploy/.env.cloud.example  <user>@<host>:/srv/fit-app/.env   # 然后改真值
scp deploy/nginx/site.conf.example <user>@<host>:/etc/nginx/conf.d/<site>.conf
```

## 线上代码只能靠发版换（没有「手工同步」这条捷径）

线上跑的是镜像，不是源码：服务器上**没有仓库、没有 git**，编排里也**没有 `build:`**（小机器跑 Maven 必 OOM，
构建一律在流水线上做）。所以「把新代码同步到线上」= 换镜像 tag，而镜像由发布流水线构建。

真要绕开版本 tag 也有一条手工路径：本机构建后推一个非版本 tag，再到服务器上 `bash server-deploy.sh <tag>`——
脚本不挑 tag，能跑。但代价是 `.current-version` / `.version-history` 会记下它，回滚链从此混入一个与 git tag
对不上的落点；同时绕开版本门禁与迁移重放。**结论：要动线上就发版；只想看效果用本机，别动线上。**

compose 与本目录下的脚本倒是可以不发版就换（`scp` 到 `/srv/fit-app/compose/`），但下次发版会被整体覆盖——
这是「服务器上的编排 = 这个 tag 的编排」这条设计的一部分，仓库始终是真相源。

## 备份与恢复

设计见 [ADR 0026](../docs/adr/0026-photo-storage-and-backup.md)。**日常不需要人工介入**：

| 时机 | 做什么 | 脚本 |
|---|---|---|
| 每次发版、`up -d` **之前** | 强制 dump 一次库；**失败即中止部署** | `backup-mysql.sh` |
| 每天 03:30 | 给餐照目录打一份硬链接快照（保留 30 册） | `backup-uploads.sh` |
| 每天 03:35 | 库全量 dump（保留 30 份） | `backup-mysql.sh` |
| 每天 03:50 | 推一份异地副本到 OSS | `backup-to-oss.sh` |
| 每次发版、部署**之后** | 用最新备份做一次恢复演练 | `restore-verify.sh` |

⚠️ 顺序是**先图片、后数据库**。两者没法在同一瞬间完成，顺序决定「万一卡在中间」的最坏结果：
先图后库最坏是多一张没人认领的图片（无害）；反过来会出现「记录指向不存在的图」（空号，最麻烦）。

**装定时任务**（只在服务器上跑一次；幂等，重复跑不会重复添加）：

```bash
bash /srv/fit-app/compose/scripts/server-install-cron.sh
```

**手工跑 / 看日志**：

```bash
cd /srv/fit-app/compose/scripts
bash backup-uploads.sh && bash backup-mysql.sh && bash backup-to-oss.sh   # 立刻备一轮
bash restore-verify.sh                                                   # 验最新那份能不能恢复
tail -50 /var/log/fit-app-backup.log
```

## 从备份恢复

```bash
# 结构化数据：把某一份灌回库（先停后端，避免恢复期间继续写入）
gunzip -c /srv/fit-app/backups/db/fit_app-<时间戳>.sql.gz \
  | docker exec -i -e MYSQL_PWD="$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' /srv/fit-app/.env)" \
      quizzy-mysql mysql -uroot fit_app

# 图片：从某一天的快照整册拷回（快照里那天的样子就是恢复目标）
rsync -a --delete /srv/fit-app/backups/uploads/2026-10-16/ /srv/fit-app/uploads/

# 只丢了一张：从快照里单独捞回来
cp /srv/fit-app/backups/uploads/2026-10-16/1/2026-10-12/lunch.jpg /srv/fit-app/uploads/1/2026-10-12/
```

更坏的情况（整机没了）：从 OSS 拉回 `oss://<桶>/fit-app/db/` 与 `.../uploads/`，再按上面两步灌回。
⚠️ 这条路径**没有演练过**——恢复演练脚本目前只覆盖「库能恢复成可用的库」，整机重建属已知空白。

## 回滚

```bash
cd /srv/fit-app/compose/scripts
bash server-rollback.sh            # 回到上一个已上线版本
bash server-rollback.sh 1.0.3      # 或显式指定
```

⚠️ **回滚只换代码，不回滚数据库**——迁移是不可逆的，所以部署前必须先备份
（2026-10-16 起这一步由 `server-deploy.sh` 在 `up -d` 之前**强制**执行，备份失败会中止部署）。
