# 更新日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式。
版本号以 `git tag` 为唯一真相源，手写点（`frontend/package.json`、`backend/pom.xml`）由 `scripts/check-version.sh` 在推 tag 时校验。

## [Unreleased]

<!-- 改了什么就往这里加；打 tag 时把这一节固化成一个版本段。 -->

### Added

- 前端换代：uni-app（Vue 3）工程，12 页路由骨架与设计语言变量体系（`uni.scss`）
- 前端页面：主页（含饮食记录预留卡）／训练会话（组次录入、计时、上次数据）／完成汇总／训练日志／训练安排（周表驱动「开始训练」）
- 后端第一期：认证（注册／登录，JWT）；动作库（59 个预设动作＋自定义）；训练记录（提交／列表／详情／备注／删除／某动作上次数据）
- 数据库迁移 V2–V5（用户／动作库／训练三表／预设动作种子）；后端单元测试

### Changed

- 前端工程由 Vue3 网页骨架整体换代到 uni-app（旧骨架退役，见 ADR 0002）
- CI：前端构建命令适配 `build:h5`；lockfile 重建为全平台产物（修复 CI 上 rollup 二进制缺失）

### Fixed

## [0.1.0] - YYYY-MM-DD

### Added

- 项目骨架：文档体系 + 后端 + 前端 + Docker + CI
