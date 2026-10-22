# 设计 skill 清单（外部工具，装在项目级、不入库）

**目的**：存档**为什么只用这些、装到哪一步、哪部分被舍弃**——回答「当初为什么这么装」，避免以后重新纠结或误判成遗漏。

**读者**：改前端 UI 的人 / 未来的自己 / AI。

**真相源**：本文件。**用途与禁区**的速查表在项目规则里（每次会话自动加载，那里才是「开工前该看」的）；本文件是**档案**，记录动机与完整性——两处**允许重叠，但速查口径以规则为准**。

## 一览

| # | Skill | 角色 | 什么时候用 | 备注 |
|---|---|---|---|---|
| 1 | **impeccable** | 方向 + 确定性门禁（设计总导演） | 定方向 / 整体重设计 / 抽设计系统（`init` / `document` / `extract`），及 `critique` / `audit` / `polish` | 引擎首次调用会从远端拉平台二进制；本项目**不装 hooks**（探测器只在被主动调用时跑） |
| 2 | **make-interfaces-feel-better** | 细节工艺 + 审查格式 | 圆角、阴影 / 描边取舍、悬停 / 按下态、图标描边、动效克制、字体平滑 | |
| 3 | **web-design-guidelines** | 无障碍 / 合规审查 | `review my UI` / `check accessibility` / `audit design` | 每次从远端拉最新规则（**需联网**） |
| 4 | **shadcn-vue** | 组件用法（官方） | 用 shadcn-vue 组件：`add` / `search` / `docs` / preset / 调试 | ⚠️ `user-invocable: false`，**只能 AI 自己用，@ 不到** |
| 5 | **ui-ux-pro-max** | 检索型资料库（查询台） | 查色板 / 字体搭配 / 风格 / UX 规则 / 图标 / 图表 / 栈实现 | ⚠️ **只查不写 —— 禁用 `--persist` / `--force`** |

**本机没装某个 skill 就跳过它**，并在这里补一行说明（避免下次误判成「漏了」）。

## 共同约定（适用的都写在这里，不逐行重复）

- **都不设 `alwaysApply`**：不常驻注入上下文。
- **都装在项目的 `.claude/skills/<名字>/`、不入库**（`.gitignore` 已忽略）：只对本项目生效，换机器不跟着走，按下面「来源」重装；**按描述命中、非自动加载**，开工时**点名（或 @ 提及）最稳**。

## 来源

都是作者本人或官方组织的仓库。**只取下表那一个目录**，整个复制成 `.claude/skills/<名字>/`；不要装同名的第三方转载版（市面上有不少镜像 / 改版）。

| Skill | 仓库 | 默认分支 | 仓库内目录 |
|---|---|---|---|
| impeccable | `pbakaus/impeccable` | `main` | `.claude/skills/impeccable/` |
| make-interfaces-feel-better | `jakubkrehel/make-interfaces-feel-better` | `main` | `skills/make-interfaces-feel-better/` |
| web-design-guidelines | `vercel-labs/agent-skills` | `main` | `skills/web-design-guidelines/` |
| ui-ux-pro-max | `nextlevelbuilder/ui-ux-pro-max-skill` | `main` | `.claude/skills/ui-ux-pro-max/` |
| shadcn-vue | `unovue/shadcn-vue`（shadcn-vue 官方仓库） | `dev` | `skills/shadcn-vue/` |

**取法**：对每一行做一次稀疏克隆，再把那个目录复制进来（在临时目录里做，别克隆进仓库）——

```bash
git clone --depth 1 --filter=blob:none --sparse -b <分支> https://github.com/<仓库>.git <临时目录>
git -C <临时目录> sparse-checkout set --no-cone '/<仓库内目录>'
cp -r <临时目录>/<仓库内目录> .claude/skills/
```

⚠️ Windows 的 Git Bash 会把 `'/<仓库内目录>'` 这种以 `/` 开头的参数改写成 Windows 路径，结果什么都检不出来；先 `export MSYS_NO_PATHCONV=1` 再跑。
各仓库 README 里的 `npx skills add …` 也能装，但默认装到用户级，不合本项目口径。

## 逐项细节

### 1. impeccable

| 组件 | 状态 | 说明 |
|---|---|---|
| `SKILL.md` | ✅ | |
| `reference/*.md` | ✅ | |
| `scripts/` | ✅ | 提供确定性的上下文扫描 |
| 引擎二进制 | ⚠️ **运行期拉取** | 首次调用从远端按版本锁拉取 |
| `hooks/` | ❌ **未装** | 探测器只在被主动调用时跑 |

**回退方式**：删掉 skill 下的 `scripts/` 与引擎缓存目录，即回到「只用 guidance」。

### 5. ui-ux-pro-max

**⚠️ 唯一硬约束**：`--persist` 会往**仓库根**写 `design-system/<slug>/MASTER.md`（自称 "Global Source of Truth"）→ 与本项目「设计真相源 = `frontend/src/styles/tokens.css`」冲突。**禁用 `--persist` / `--force`**；`.gitignore` 已加 `design-system/` 兜底。注意：**不带 `--persist` 的 `--design-system` 只打印、可用**，被砍的只是「写盘副作用」。

**环境前提**：搜索脚本需 **Python 3.x**（`python` / `python3` / `py -3` 任一）。

## 本项目的调整记录

<!-- 增删 skill、改安装范围时在这里追加一行：改动 + 理由。别改上面的历史条目。 -->

- 2026-10-22：安装范围从用户级改为项目级 `.claude/skills/`、仍不入库；补「来源」一节。理由：只给本项目用，不污染其他项目；来源此前没记，换机器时只能重新搜，且容易装到同名的第三方转载版。代价：`.claude/skills/` 是 Claude Code 的约定，CodeBuddy 等其他工具读不到（按「没装就跳过」处理）。
