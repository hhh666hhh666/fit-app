# 待办：GitHub 账号计费锁定

创建：2026-11-06 · 状态：待办 · 优先级：高

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
