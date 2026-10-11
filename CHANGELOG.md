# 更新日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式。
版本号以 `git tag` 为唯一真相源，手写点（`frontend/package.json`、`backend/pom.xml`）由 `scripts/check-version.sh` 在推 tag 时校验。

## [Unreleased]

<!-- 改了什么就往这里加；打 tag 时把这一节固化成一个版本段。 -->

## [0.1.0] - 2026-10-12

首个可用版本：训练记录闭环（动作库／组次／计划／周安排／日志／统计／身体档案）＋ 认证，云端部署到 `fit.11-2.cn`。

### Added

- 前端换代：uni-app（Vue 3）工程，12 页路由骨架与设计语言变量体系（`uni.scss`）
- 前端页面：主页（含饮食记录预留卡）／训练会话（组次录入、计时、上次数据）／完成汇总／训练日志／训练安排（周表驱动「开始训练」）
- 后端第一期：认证（注册／登录，JWT）；动作库（59 个预设动作＋自定义）；训练记录（提交／列表／详情／备注／删除／某动作上次数据）
- 数据库迁移 V2–V5（用户／动作库／训练三表／预设动作种子）；后端单元测试
- 后端第二期（计划与周安排）：迁移 V6（`plan`／`plan_exercise`／`week_schedule` 三表＋`workout.plan_id` 来源弱引用）；计划 CRUD（编排整体替换、同名拦截、删计划连带清周表引用）与周安排读写接口；后端测试扩至 32 用例
- 后端第三期（统计与 PR）：迁移 V7（`workout_set.pr` 提交时点布尔）；PR 判定（逐组对比该动作历史 weight×reps，严格大于才刷新、首次即最佳、未挂动作库不判定）；`GET /api/stats`（周期次数与时长＋近 4 周柱状＋各动作 PR 归档现算）；后端测试扩至 39 用例
- 前端：训练计划列表／计划编辑（动作库点选模式、目标组次、上移下移）／我的页／动作库页（浏览＋点选双模式、自定义动作创建）；周安排、主页「上次训练」卡与统计条、训练会话切真接口（会话提交带来源计划）
- 前端：统计页（周期 tab／双格卡／近 4 周自绘柱状／PR ARCHIVE 列表，最近刷新的 PR 荧光）；汇总页 PR 组 chip 与主页上次训练行的荧光标记
- 后端第四期（身体档案）：迁移 V8（`body_record`：测定日期＋体重＋四围＋备注，记哪项填哪项）；`GET /api/body` 一屏总览（当前体重／较上周／阶段最低判定／体重序列／最新围度）、体重明细（相邻 DIFF 服务端算）、录入（全空拦截＋数值范围校验）；后端测试扩至 45 用例
- 前端：身体档案页（当前体重卡含「阶段最低 · PR」荧光、基础围度四格、近 3 个月 canvas 折线自绘、体重明细 DIFF、录入弹层）
- 前端：**登录守卫**——`utils/guard.ts` 的 `ensureLogin()` 读本地 token，空则回登录页；12 个业务页在 `onShow`（会话页 `onLoad`）首行调用并 `return`
- 部署链路（上云准备）：`frontend/Dockerfile` ＋ `nginx.conf`（H5 产物 `dist/build/h5` → nginx，`/api/` 反代到 `server:8080`）＋ `frontend/.dockerignore`；`release.yml` 的 ACR／HOST／USER 填真值；部署方案见 ADR 0005

### Changed

- 前端工程由 Vue3 网页骨架整体换代到 uni-app（旧骨架退役，见 ADR 0002）
- CI：前端构建命令适配 `build:h5`；lockfile 重建为全平台产物（修复 CI 上 rollup 二进制缺失）
- scope #10 实施细化：动作「下架」与计划「物理删除」的删除机制分离（见 `docs/design/数据模型.md`）
- **云上编排改方案 A**：不再自带 MySQL，复用 quizzy 的 `quizzy-mysql` 容器里的 `fit_app` 库（external 网络 ＋ 容器名直连 ＋ `createDatabaseIfNotExist`）；web 端口 8081 → **8082**（8081 被 quizzy-web 占）；`server-deploy.sh` 只检查外部 MySQL 在跑，绝不上拉／重建别人的容器
- scope #6 收窄：训练记录**不录休息时长**（设计稿无该字段、第一阶段无「训练密度」视图读它）；自动计时器本就在「明确不做」里，口径不变

### Fixed
