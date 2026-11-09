# API

**目的**：讲清「真实接口在哪看」与通用约定。

**读者**：对接接口或改 Controller 的人。

**真相源**：**接口清单以运行时为准**（springdoc 与各 Controller）。本文档**刻意不维护 endpoint 表**——那类清单必然与代码漂开。

## 怎么看真实接口

启动后端后打开接口文档页（路径见 `backend/src/main/resources/application.yml` 的 `springdoc` 段），或用 `/v3/api-docs` 拿原始 JSON。

## 通用响应信封

所有接口统一返回同一个信封：`{ code, message, data, timestamp }`。

- `code = 0` 表示成功，其余见 `ResultCode` 枚举（后端 `common/ResultCode.java`）。
- 分页接口的 `data` 是 `PageResult`：`{ list, total, page, size }`。
- **字段名与错误码以代码为准**，本文不复制清单。

## 错误约定

- 业务失败抛 `BusinessException`（带业务码），由全局异常处理器转成信封。
- 参数校验失败统一 `400`，message 里带字段名。
- 数据库等基础设施的**瞬时**故障（如共用的 MySQL 容器正在重启）：返回 `503` 与「服务正在重启，请稍后重试」。
  写路径上的这类失败服务端**已自动重试过**（见 `common/TransientRetry.java`），走到这里说明重试也没扛过去——
  客户端重发一次即可，不必换图或改参数。
- 未预期异常统一 `500`，响应里不暴露堆栈。

## 鉴权与数据边界

- 除白名单路径外的 `/api/**` 需要 `Authorization: Bearer <token>`。
- 白名单与跨域策略见后端 `config/WebConfig.java` 的注释。
- **令牌带版本号**（[ADR 0050](../adr/0050-account-page-password-rename-and-deletion.md)）：`security/JwtInterceptor.java` 每次请求都查一次当前用户的令牌版本，对不上（改过密码）或用户不在了（注销）一律按 401 处理。老令牌不带版本，按 0 算。
- **上传大小**：单张图片上限由 `fitapp.storage.photo-max-mb` 一处配置（默认 50MB，[ADR 0051](../adr/0051-photo-limit-50mb-and-avatar.md)），超了返回 400「照片超过 50MB」；比它再大一两 MB 会在容器层被拦，返回 400「文件太大了」。原图读取接口按文件流式返回。
- ⚠️ **路由层只挡「没登录」，不挡「没有数据权限」**：越权（拿别人的 id 来读写）必须由服务端判定，并在接口层测试里断言它会失败。

## 账号接口

`/api/account`（[ADR 0050](../adr/0050-account-page-password-rename-and-deletion.md)）走登录态 JWT，字段以接口文档运行时为准。有三处要注意：

- **改用户名**（`PUT /api/account/username`）和**注销**（`POST /api/account/delete`）都要带当前密码；注销还要带一遍用户名，对不上就拒绝。
- **改密码**（`PUT /api/account/password`）返回新的 `LoginVO`：客户端要换上新令牌，旧令牌（包括本机的）随即失效。
- **注销**立刻删除这个用户在所有表里的数据和 `{photo-dir}/{userId}/` 下的全部文件，不进回收站。要删哪些表由 `AccountService.USER_TABLES` 和 `AccountDeletionMapper` 决定，新建表忘了登记会让 `AccountTablesCoverageTest` 失败。

## 头像接口

`/api/account/avatars`（[ADR 0051](../adr/0051-photo-limit-50mb-and-avatar.md)）走登录态 JWT，字段以接口文档运行时为准。有四处要注意：

- **坐标口径**：宽高与裁剪框（`x`、`y`、`size`，正方形）一律按**摆正后**的原图像素算——服务端读 JPEG 的 EXIF 方向，和浏览器显示的方向一致。出界的框会被收进图内，不报错。
- **上传**（`POST`，multipart：`file` ＋ `x`、`y`、`size`）只收 JPEG/PNG（JDK 读不了 WebP），直接设为当前头像。服务端存原图，另生成缩略图（历史列表）、预览图（裁剪框用，长边 1280）和 512×512 的裁剪结果。
- **重裁**（`PUT /{id}/crop`）覆盖这一项的裁剪结果并刷新使用时刻，于是它排到最前面并成为当前头像，不另起一条。
- **取图**（`GET /{id}/image?kind=crop|thumb|view|original`）直出二进制；删除（`DELETE /{id}`）进回收站，回收站类型是 `avatar`；`DELETE /current` 是「不用头像」。

## 日记接口

`/api/diary`（[ADR 0044](../adr/0044-diary-markdown-photos-and-record-refs.md)）走登录态 JWT，外部 agent 令牌访问不了。有两处要注意：

- **图片读取**（`/api/diary/photos/{id}` 与 `/thumb`）直出二进制，图已删返回真实的 HTTP 404，前端据此显示「图片已删除」。
- **记录引用**（`/api/diary/refs`）解析不到的键给 `exists=false`，不报错：一篇日记里有一条引用失效很正常。

## 回收站接口

`/api/trash`（[ADR 0047](../adr/0047-trash-snapshot-table.md)）走登录态 JWT，字段以接口文档运行时为准。有三处要注意：

- **删除接口语义变了**：一次训练、日记、餐照、日记图片、身体档案、计划、自定义动作、自建食物的删除接口都是移到回收站，不再物理删除。餐次条目和食物组合仍是物理删除。
- **恢复**（`POST /api/trash/restore`）任一条不归属则整批不动；有话要告诉主人时带 `note`；丢掉的训练带 `session`，由前端写回本机。
- **丢掉的训练**（`POST /api/trash/sessions`）按用户和开始时刻去重，重复补传算成功。

餐照接口按 id：`/api/diet/photos/{id}` 与 `/thumb`，某一餐的列表是 `/api/diet/photos/of-meal`（[ADR 0045](../adr/0045-meal-photos-multiple-per-meal.md)）。

记一餐的 AI 识别（[ADR 0052](../adr/0052-meal-ai-recognition-temporary-entry.md)）：`POST /api/food-recognition/meal` 照片进、临时食物草稿出，不落库，营养是每 100、`grams` 是这一份的量；`POST /api/diet/meals/custom` 记一条临时条目（营养按每 100 传，钠可空），条目的 `foodId` 为空。条目返回都多带 `sodium`，老条目为 `null`。

识别明细（[ADR 0053](../adr/0053-recognition-log-detail.md)）：`GET /api/food-recognition/logs` 分页列表不带 `rawOutput`、`requestJson`、`responseJson`；`GET /api/food-recognition/logs/{id}` 给一条的全部字段，不是自己的返回 404。

## 管理端接口

`/api/admin/**`（[ADR 0048](../adr/0048-admin-console-and-preset-archive.md)）给 `admin/` 管理端用，走登录态 JWT，字段以接口文档运行时为准。有四处要注意：

- **鉴权多一道**：JWT 之后由 `security/AdminInterceptor.java` 查当前用户的管理员标记，不是管理员返回业务码 403「没有权限」（HTTP 仍是 200）。每次请求都查库，改了标记立即生效。
- **没有删除接口**：预置的删除就是下架，`POST .../archive` 和 `.../unarchive` 都收 `{ ids }`，批量里混进非预置的 id 就整批拒绝。重新上架会清掉所有用户对它的隐藏。
- **导入分两步**：`POST /api/admin/foods/import/preview` 只预检不写库，`POST /api/admin/foods/import` 重新预检一遍再在一个事务里写入；两者返回同一种结构（新增数、更新数、无效清单）。格式与 `GET /api/admin/foods/export` 一致，和 App 自建食物的 JSON 只多了 `id`、`category` 两个键（读取逻辑共用 `FoodJsonReader`）。
- **App 这边的变化**：食物和动作列表多了 `archived` 字段，只会出现在用过它的人看到的已下架预置上；对这类预置调 `DELETE /api/foods/{id}` 或 `DELETE /api/exercises/{id}` 是对自己隐藏，回收站类型是 `preset_food`／`preset_exercise`。

## 外部 agent 取数接口

面向外部调用方的只读接口（[ADR 0043](../adr/0043-external-agent-read-api.md)），有几处与上面的约定不同：

- **文档**：外部调用方拿不到本仓库，所以这组接口有一份随后端发布的说明，真相源在 `backend/src/main/resources/agent/agent-api.md`，运行时由 `GET /api/agent/docs` 提供。**改这组接口要同改它。**
- **鉴权**：只认 agent 令牌，不认登录态 JWT；反过来 agent 令牌也访问不了 App 的接口。令牌的生成与吊销（`/api/agent/token`）走 JWT。
- **失败响应**：令牌校验失败返回真实的 HTTP 401（App 的接口是 HTTP 200 ＋ 业务码）；其余错误与全局约定一致。
- **空值**：null 字段明确写出，不像全局配置那样省略。
- **`history` 是流式写出的**：中途失败时状态码已发出，响应是残缺的 JSON，所以写出时关掉了 Jackson 的自动补全括号（见 `AgentDataService`）。
