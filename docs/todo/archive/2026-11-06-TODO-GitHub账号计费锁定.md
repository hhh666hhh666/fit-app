# 待办：GitHub 账号计费锁定

创建：2026-11-06 · 状态：已解决 · 优先级：高

## 背景

2026-11-06 起 GitHub Actions 全部拒跑，提示「account is locked due to a billing issue」；账单页写着「This account is disabled because we have not been able to charge it for another month」。核对过：GitHub Free ＋ Copilot Free，没绑付款方式，付款记录为空，各月实际应付都是 $0（quizzy 10 月 Actions 用量按价目约 $18.6，被折扣全额抵掉）。已提交客服工单。CI 与发布流水线在解锁前都跑不了，0.33.0 是按下面的手工路径发的。

## 可选方案

- **方案甲**：等客服解除锁定 —— 代价：期间每次发版都要走手工路径。
- **方案乙**：绑一张卡，同时把各项预算设成 $0 并开启达到即停 —— 代价：绑卡要主人亲自操作，以后可能真产生费用。

➡️ 倾向：先等客服（甲），急着发版再考虑乙。

## 待确认

- 客服回复后，用 `gh run rerun 42239529549`（只跑测试、不碰线上）验证是否真的解锁：几秒内失败并提示锁定就是没解。
- 解锁后重跑一次 v0.33.0 的 Release（`gh run rerun 42239528804`）：镜像已在 ghcr 上、线上已是 0.33.0，重跑等于按流水线再部署一遍同一版本，可以用来确认流水线本身没问题。

## 手工发版路径（解锁前用，0.33.0 实际走过）

和流水线同序：`check-version.sh` → 临时 mysql:8 上按 `sort -V` 重放全部迁移 → 本机 `docker buildx build --load` 两个镜像（后端传 `MAVEN_MIRROR=central`；前端加 `--network=host`，否则 `npm ci` 约 85 秒后报「Exit handler never called」）→ `gh auth token | docker login ghcr.io` 后推送（`gh` 需要 `write:packages` 权限）→ scp compose 与 `scripts/` 下那 9 个脚本到 `/srv/fit-app/compose/` 并 `chmod +x` → 服务器上 `server-deploy.sh <版本>` → `restore-verify.sh`。

⚠️ 前端镜像的 `npm ci` 是按 lockfile 里的 `resolved` 地址下包的，`--registry` 改不动它们：`frontend/package-lock.json` 的地址写的是 npmmirror，本机连不上 npmmirror 时，npm 会在约 85 秒后报「Exit handler never called」（`--network=host` 也救不了）。绕法是从 tag 用 `git archive` 导出 `frontend/` 与 `admin/` 到临时目录，把那份 lockfile 里的 `https://registry.npmmirror.com/` 换成 `https://registry.npmjs.org/`，再拿它当构建上下文。包和 integrity 都一样，仓库本身不用改。

## 更新记录

- 2026-11-06：创建；0.33.0 手工发布成功。
- 2026-11-07：0.33.1 照手工路径发布成功（Actions 仍秒失败，锁没解）；补上 npmmirror 连不上时的构建绕法。
- 2026-11-09：0.34.0 照手工路径发布成功（Actions 仍秒失败，锁没解）；npmmirror 绕法照用。
- 2026-11-09：0.35.0 照手工路径发布成功（Actions 仍秒失败）；构建前端镜像时撞上 apt「Release 文件尚未生效」，原因是构建机时钟慢了约 21 分钟——手工发版前先核对本机时间。
- 2026-11-09：0.36.0 照手工路径发布成功（Actions 仍秒失败）；构建前先核对了本机时间。
- 2026-11-11：0.37.0 照手工路径发布成功（Actions 仍秒失败；迁移 V42）；npmmirror 仍连不上，照用绕法；推 web 镜像时 ghcr 断了两次，重推即可。另按 ADR 0055 用 `scripts/exercise-dataset/sync-media.sh` 把动图同步到了服务器。
- 2026-11-15：0.38.0 照手工路径发布成功（迁移 V43）；npmmirror 仍连不上，照用绕法；构建前核对了本机时间。本机经代理访问线上域名 TLS 握手失败，改在服务器上用 127.0.0.1 与公网域名各验了一次，都是 200。
- 2026-11-15：0.38.1 照手工路径发布成功（Actions 仍秒失败；无新迁移，跳过重放）；npmmirror 绕法照用；推 web 镜像时 ghcr 超时一次，重推即可；恢复演练通过，线上 127.0.0.1 与公网域名都是 200。
- 2026-11-15：0.39.0 照手工路径发布成功（无新迁移，跳过重放）；构建前核对了本机时间；npmmirror 绕法照用；两个镜像一次推成；恢复演练通过，线上 127.0.0.1 与公网域名都是 200，新接口 `/api/food-recognition/stats` 未登录返回 401。
- 2026-11-22：0.40.0 照手工路径发布成功（迁移 V44–V47，先在临时 mysql:8 上重放过）；npmmirror 绕法照用；两个镜像一次推成；构建前核对了本机时间。部署后的恢复演练因迁移新建了两张表而误报（见 [恢复演练新建表误报](../2026-11-22-TODO-恢复演练新建表误报.md)），新做一份备份再演练通过；线上 127.0.0.1 与公网域名都是 200。
- 2026-11-22：0.41.0 照手工路径发布成功（迁移 V48，改的是老数据：先用 0.40.0 镜像在临时 mysql:8 上迁到 V47、插几条旧条目，再用 0.41.0 镜像跑 V48 核对结果）；npmmirror 绕法照用；两个镜像一次推成；构建前核对了本机时间。线上 316 条老条目的时刻已改成默认值；恢复演练通过（V48 不新建表，没触发误报）；线上 127.0.0.1 与公网域名都是 200。
- 2026-11-23：**Actions 恢复了**——推 v0.42.0 时 CI 四项全绿，Release 也真跑起来了，但卡在「迁移 · 临时库重放」：V48 引用 Flyway 历史表，命令行重放没有这张表（V48 当时走手工路径，从没进过 CI）。V48 已上线不能改，修在 `release.yml`（重放前建空的同名替身表，提交 `32e3b77`，本地重放全绿）。0.42.0 照手工路径发布成功（迁移 V49：三餐时刻统一、`food.cook_state`、`bowel_record.color`，先在临时 mysql:8 上重放并用样例行验过统一逻辑）；本机网络这次直连 npmjs 也照用了 lockfile 换源；两个镜像一次推成；恢复演练通过（V49 不新建表）；线上 127.0.0.1 与公网都是 200。下一个 tag 由流水线完整跑一遍（含 SSH 部署）确认无误后，本待办结案。
- 2026-11-23：**结案**。推 v0.43.0 后 Release 流水线四步（版本门禁、迁移重放、构建推镜像、SSH 部署含恢复演练）全部跑通，线上两个容器都是 0.43.0、公网 200；以后发版走流水线，上面的手工路径只作 Actions 再出故障时的后备。
