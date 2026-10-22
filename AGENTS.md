# fit-app · 给 AI 的规矩

适用任何 AI 工具。**权威版本在 `.codebuddy/rules/`（五条，每次会话自动加载）；本文件只做路由与收尾清单，不复述规则内容**——两边冲突以那边为准，新增或改名规则要同步下面这张表。

Spring Boot + Vue 的自用项目。领域词看 `CONTEXT.md`，文档总索引看 `docs/README.md`。

## 纪律 → 去哪读全文

- **未决事项必须落盘**：任何没定的事，本次回复结束前必须变成 `docs/todo/` 下的文件 → `.codebuddy/rules/unresolved-items.md`
- **改代码 = 改文档**：改了行为就顺手改文档；移动 / 删除文件后全仓搜旧名 → `.codebuddy/rules/docs-with-code.md`
- **会话工作台**：任务要动文件，动手**之前**先建 `docs/worklog/` 工作台 → `.codebuddy/rules/session-log.md`
- **外部设计 skill**：用哪个、什么禁区（`ui-ux-pro-max` 禁用 `--persist`） → `.codebuddy/rules/design-tools.md`
- **本机 SSH 与服务器入口**：密钥位置、连接别名与红线 → `.codebuddy/rules/server-ssh.md`

规则的体积预算：**单条 ≤ 100 行**，常驻规则越少越好。

纪律的形态是三件套：规则让 AI **知道**，`/wrap-up` 让 AI **记得做**，守卫脚本 + CI job 让 AI **不做就过不去**——能机械验的别只靠提示词。

## 本机开发环境：仅供参考，不是真相

项目会在不同机器上开发，本机装了什么、装在哪只对当下那台机器成立。

- **非必要不把本机环境写进仓库**：安装路径、盘符、工具具体版本、代理这类事实不入库；确需写时只写项目自身的硬要求（如「JDK 21」），不写某台机器的现状。
- **仓库里已有的环境描述只当线索**：动手前以实测为准（`java -version`、`where.exe`、`Test-Path` 之类），与文档不符就按实际来，不要照着文档里的路径或版本去找、去装。

## 改前端 UI 时的入口

该用哪几个设计 skill、禁区与安装口径 → `.codebuddy/rules/design-tools.md`；设计系统的真相源在 `frontend/src/styles/tokens.css`（**改颜色 / 间距 / 圆角只改它**）。

## 收尾（每次对话结束前）

1. 扫本次有没有「提到但没做」的事 → 落 `docs/todo/`
2. `git status --porcelain` 列改动 → 补文档与引用
3. 跑 `bash scripts/check-doc-links.sh`，修到全绿（CI 里有同名 job 会再验）
4. 回复末尾给出：改了哪些文件 / 新增或更新了哪些 TODO / 建议的 Conventional Commits 切分
