# 云上服务名专属化：fit-app-server，通用名只留作本网络别名

Status: accepted

2026-10-12 的撞名事故：fit-app 后端的 compose 服务名是通用名 `server`，而它要与 quizzy 共用 Docker 网络（为连 `quizzy-mysql`）。compose 会自动把服务名加为所在**每个**网络的别名、且**无法按网络移除**——于是 quizzy 网络里的 `server` 同时指向两家后端，Docker DNS 轮询把 quizzy 约一半 `/api` 请求发到了 fit-app（两家账号体系不通，表现为「注册/登录随机失败」）。quizzy 侧当天已改用容器名自保；fit-app 侧根治：**服务名改为 `fit-app-server`**，同时保住 web 镜像零改动。

## Considered Options

- **保持服务名 `server`，只靠对方改用容器名**：否——通用名在任何共享网络里都可能再撞（下次未必是 quizzy），隐患留在自己身上不算修好。
- **改服务名 ＋ 改 web 镜像内 nginx 的目标名（`server` → 新名）**：否（本期）——更「干净」，但要重新构建发布 web 镜像；而且镜像里的名字同样是隐式约定，只是从「网络别名」搬进了镜像内容，并不更显式。
- **改服务名 ＋ 在 fit-app 自己的网络里保留别名 `server`**（选定）：零镜像改动；代价是多一层「别名是兼容层、不能随手删」的隐式知识——写进 compose 注释与本卡。

## Consequences

- 共享网络（`quizzy_default`）里 fit-app 只以 `fit-app-server` 出现；`server` 别名只存在于 fit-app 自己的网络（`fit-app_default`）。
- ⚠️ **别名 `server` 不能删**：web 镜像内的 nginx 用它找后端（`frontend/nginx.conf` 的 `set $backend http://server:8080`）。要清理它，必须先改 nginx.conf 并重发 web 镜像。
- 引用已同步一轮：`scripts/server-deploy.sh`（含作用域参数的 `server` → `fit-app-server`）、ADR 0005 追加「后续」注、历史文档中的服务名表述；服务器那份 compose 手工同步（发版链路会在下次部署时继续以仓库版本覆盖）。
- 命名规则外推：**共享 Docker 网络里不放通用词**（`server` / `app` / `api` …），名字带项目前缀。
