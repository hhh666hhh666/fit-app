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
- ⚠️ **路由层只挡「没登录」，不挡「没有数据权限」**：越权（拿别人的 id 来读写）必须由服务端判定，并在接口层测试里断言它会失败。

## 日记接口

`/api/diary`（[ADR 0044](../adr/0044-diary-markdown-photos-and-record-refs.md)）走登录态 JWT，外部 agent 令牌访问不了。有两处要注意：

- **图片读取**（`/api/diary/photos/{id}` 与 `/thumb`）直出二进制，图已删返回真实的 HTTP 404，前端据此显示「图片已删除」。
- **记录引用**（`/api/diary/refs`）解析不到的键给 `exists=false`，不报错：一篇日记里有一条引用失效很正常。

## 外部 agent 取数接口

面向外部调用方的只读接口（[ADR 0043](../adr/0043-external-agent-read-api.md)），有几处与上面的约定不同：

- **文档**：外部调用方拿不到本仓库，所以这组接口有一份随后端发布的说明，真相源在 `backend/src/main/resources/agent/agent-api.md`，运行时由 `GET /api/agent/docs` 提供。**改这组接口要同改它。**
- **鉴权**：只认 agent 令牌，不认登录态 JWT；反过来 agent 令牌也访问不了 App 的接口。令牌的生成与吊销（`/api/agent/token`）走 JWT。
- **失败响应**：令牌校验失败返回真实的 HTTP 401（App 的接口是 HTTP 200 ＋ 业务码）；其余错误与全局约定一致。
- **空值**：null 字段明确写出，不像全局配置那样省略。
- **`history` 是流式写出的**：中途失败时状态码已发出，响应是残缺的 JSON，所以写出时关掉了 Jackson 的自动补全括号（见 `AgentDataService`）。
