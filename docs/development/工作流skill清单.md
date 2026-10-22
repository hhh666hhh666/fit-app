# 工作流 skill 清单（外部工具，装在项目级、不入库）

**目的**：记下非设计类的外部 skill **从哪来、为什么装、和本项目约定怎么对齐**——换机器重装时照着做，也防止以后误判成「漏了」或「装错版本」。设计类的见 [《设计 skill 清单》](../design/设计skill清单.md)，安装口径（项目级 `.claude/skills/`、不入库）与那边一致。

**读者**：要重装 skill 的人 / 未来的自己 / AI。

**真相源**：本文件。与本项目文件约定的映射是**规则**，在 `.codebuddy/rules/docs-with-code.md` 的「外部 skill 与本项目口径的映射」一节，以那里为准。

## 一览

| Skill | 做什么 | 怎么触发 |
|---|---|---|
| **grill-with-docs** | 入口：依次调用下面两个，边追问边落术语表与 ADR | 只能手动调用（`disable-model-invocation: true`），AI 不会自己触发 |
| **grilling** | 把方案拆成决策树，按轮追问；每题附推荐答案（答 yes 即采纳）；事实由 AI 自己查，决定留给人 | 说「grill 我」或直接调用 |
| **domain-modeling** | 讨论中挑战术语、补边界场景、随时更新术语表，按需提议 ADR | 改术语表 / 写 ADR 时 |

**为什么装**：这几个约束的是**工作流程**（先把方案问透再动手），不是在补模型知识，不会随模型变强而失效。本项目的 `CONTEXT.md` 与 ADR 收录三条标准，本来就和 `domain-modeling` 同源。

## 来源

作者 Matt Pocock 的官方仓库 `mattpocock/skills`（MIT，默认分支 `main`）。**三个目录都要装**——`grill-with-docs` 只是一行调用，缺了另外两个就是空壳。

| Skill | 仓库内目录 |
|---|---|
| grill-with-docs | `skills/engineering/grill-with-docs/` |
| grilling | `skills/productivity/grilling/` |
| domain-modeling | `skills/engineering/domain-modeling/` |

**取法**与设计 skill 相同（稀疏克隆后整目录复制进 `.claude/skills/`，Windows 的 Git Bash 先 `export MSYS_NO_PATHCONV=1`），命令见《设计 skill 清单》的「来源」一节。市面上有多个同名改版（如 `alirezarezvani/claude-skills`），不要装错。

## 与本项目约定的差异（为什么不直接照它来）

| 它的约定 | 本项目 | 处理 |
|---|---|---|
| 术语表叫 `GLOSSARY.md`，粗体词＋`_Avoid_` 列表格式 | 根 `CONTEXT.md`，表格格式（词 / 含义 / 不要叫） | **不改名**：V6 迁移脚本的列注释与 4 张 ADR 引用了 `CONTEXT.md`，迁移已上线、ADR 不改，改名会留下永久断引用。规则里做映射 |
| ADR 一段话即可，其余小节可选 | `docs/adr/README.md` 要求三节必备 | 按本项目写——更严格的写法同样满足它 |
| ADR 收录三条标准 | 同样三条 | 无冲突 |

## 本项目的调整记录

<!-- 增删 skill、改约定映射时在这里追加一行：改动 + 理由。别改上面的历史条目。 -->

- 2026-10-22：新装 `grill-with-docs`、`grilling`、`domain-modeling`；不改名 `CONTEXT.md`，改在规则里映射。
