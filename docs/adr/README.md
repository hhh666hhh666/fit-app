# ADR 目录

这里放**决策结论**卡片：一条决策一张卡。查「**为什么**这么定」看这里；**被否的选项也写在同一张卡里**（`Considered Options` 一节），所以查「当时考虑过别的方案吗」同样是看这里。

## 格式

```md
# <一句话标题，中文>

Status: accepted

<两三句：定了什么、为什么需要它>

## Considered Options

- **<被否的方案>**：<为什么否掉>
- **<选定的方案>**（选定）

## Consequences

- <拍定之后的副作用与代价>
```

⚠️ **`Considered Options` 是「被否选项」唯一的存档处**，本项目没有第二个地方记它。

## 收录标准：不是每条决策都够格进来

按三条筛，**三条都中才算**：

| 标准 | 意思 |
|------|------|
| **难反转** | 改回来要动多处、或牵涉数据迁移 |
| **不看上下文会意外** | 只读代码会得出相反结论 |
| **有真实取舍** | 存在被否掉的、说得通的替代方案 |

**理由**：ADR 的价值密度在于「少而重」。把「换个 UI 组件库」这类改几个 import 就完事的事写成 ADR，只会稀释真正重要那几条。

## 编号与修改

- 编号**递增**，文件名 `NNNN-<kebab-case 标题>.md`。
- **只追加不改。** 要推翻一条决策 → **新增一条**，并在新卡里标注取代了谁。
- 需要补充或更正原卡时**追加一节**，或加一条 `> ⚠️ **后续（YYYY-MM-DD）**：…`，**不动原文**。
- **本文刻意不写条数**：每加一条 ADR 就要回头改的数字是派生事实。

## 最新决策

- [ADR 0021](./0021-session-set-order-and-undo.md)：组内顺序推进、删除组次与取消完成。
- [ADR 0022](./0022-session-pause-and-set-duration.md)：训练暂停与组次净用时落库。
- [ADR 0023](./0023-volume-metric-scope-and-display.md)：训练容量只累计力量动作、展示必带对比基准、标签按实际口径命名。
- [ADR 0024](./0024-pr-definition-heaviest-set.md)：PR 判定改为「重量优先」（旧实现取单组重量×次数最大）。
- [ADR 0025](./0025-cardio-metric-and-floors.md)：有氧专属记录口径——爬楼按层数（动作上声明口径、组次上落层数）。
- [ADR 0026](./0026-photo-storage-and-backup.md)：餐照移出数据库（图片落文件系统、库里只留路径）＋ 备份策略（库每日 dump、图片每日硬链接快照、异地 OSS、恢复演练随发版）。
- [ADR 0027](./0027-plan-per-set-targets.md)：计划目标按组设置——每组各一份次数与重量，组数的唯一真相是子表行数。
- [ADR 0028](./0028-plan-circuit-groups.md)：计划的循环组——若干动作按组轮流做，轮数取最大、组内按序列严格推进。
- [ADR 0029](./0029-daily-energy-and-calorie-gap.md)：每日总消耗（基础代谢×生活活动系数 ＋ 手填训练消耗）与热量缺口的口径。
- [ADR 0030](./0030-body-profile-fields-and-body-fat.md)：身体档案的档案字段（性别／出生日期／身高／生活活动档）与体脂率（只记录、不参与计算）。
- [ADR 0031](./0031-plan-set-floors.md)：计划目标支持逐组层数——`plan_exercise_set.floors` 与录制侧同构，服务端按动作口径只留一套值。
- [ADR 0032](./0032-plan-card-preview-before-start.md)：计划卡点击＝先弹预览再开练，编辑入口仍只在卡片右上。
- [ADR 0033](./0033-home-last-workout-metric.md)：首页「上次训练」卡的爬楼行给块合计层数、底部改成「消耗 · 时长」。
- [ADR 0034](./0034-shell-dual-track-local-and-auto-update.md)：壳改为双轨加载——本地产物优先（启动零网络等待）＋ 后台探测版本自动更新（下次启动生效）；收窄 ADR 0002 的「只加载线上地址」。
- [ADR 0035](./0035-client-cache-and-invalidation.md)：客户端数据缓存——写后失效、按域作废、不设 TTL（时间类按自然日分区）；照片走按字节 LRU 的 blob 缓存。
- [ADR 0036](./0036-cold-start-cache-persistence.md)：冷启动也读缓存——数据快照（白名单小接口）落 localStorage、餐照落 IndexedDB（128MB），启动不校验；收窄 ADR 0035 的「不落 storage」。
- [ADR 0037](./0037-tab-switch-transition.md)：主 tab 切换改为方向感知的内容层横移（只动 `__body`、底栏钉住），方向信号一次性传递，动效首次落 token。
- [ADR 0038](./0038-tabbar-switchtab-keepalive.md)：主 tab 导航改为 `tabBar` ＋ `switchTab` 保活——切页不再销毁页面，白屏／滚动位置／月份一次解决；H5 内置 tabbar 必须自己压掉。
- [ADR 0039](./0039-view-transition-tab-slide.md)：切页转场改用 View Transitions 的整页快照——旧页左出、新页右入，真连续推拉；单页入场动效退役。
- [ADR 0040](./0040-tab-slide-clone-layer.md)：切页转场改为「旧页克隆层滑出」（取代 0039）——不依赖新特性，顺带用旧页遮住新页挂载的空白。
- [ADR 0041](./0041-design-language-qingchuang.md)：设计语言由「训练日志」整体换成「晴窗」——冷调浅底、苹果式玻璃、橙色只给动作；训练入口嵌进底栏中间（四个主页面通用），进行中横幅取消。
- [ADR 0042](./0042-daily-energy-weight-as-of-date.md)：每日总消耗改用那天结束前最后一次测得的体重，之前没有就不算；收窄 ADR 0029 的「最新体重」。
- [ADR 0043](./0043-external-agent-read-api.md)：外部 agent 只读取数接口——当前状态与历史两个接口、专用令牌、服务端流式输出，不做增量、不限时间范围。
- [ADR 0044](./0044-diary-markdown-photos-and-record-refs.md)：日记——Markdown 原文存储、一天多篇，图片插在正文（共用配额，相册分两栏，无引用满 30 天清理），记录引用实时读取，不进外部 agent 接口。
- [ADR 0045](./0045-meal-photos-multiple-per-meal.md)：餐照改为一餐多张，取图删图都按 id，agent 取图地址随之改变。
- [ADR 0046](./0046-diary-photos-stay-in-album.md)：日记图片常驻相册，取消 30 天清理，删日记时可选连图一起删，写日记可从相册选图。
- [ADR 0047](./0047-trash-snapshot-table.md)：回收站用独立表存 JSON 快照、原表照常物理删除，保留 30 天，恢复按原 id 写回。
- [ADR 0048](./0048-admin-console-and-preset-archive.md)：管理端——用户表加管理员标记，独立网页放进 web 镜像的 `/admin/`；删除预置就是下架，用过的人仍能看到并可以自己隐藏。
- [ADR 0049](./0049-preset-data-source-of-truth-is-database.md)：预置数据以数据库为准，迁移脚本不再修改预置行，批量变动走管理端导入。
- [ADR 0050](./0050-account-page-password-rename-and-deletion.md)：「账号」页——昵称、改用户名、改密码（其他设备下线，靠令牌版本号）、注销即彻底删除；取代 ADR 0003 的「不做改密」。
- [ADR 0051](./0051-photo-limit-50mb-and-avatar.md)：单张图片上限提到 50MB（边收边写盘、降采样解码，不加堆）；头像保留原图与裁剪结果，历史头像切回时重新裁剪，占照片空间、不进相册。
- [ADR 0052](./0052-meal-ai-recognition-temporary-entry.md)：记一餐时 AI 识别——饭菜照片估算或营养成分表换算，记成不进食物库的临时条目；条目加钠快照；放开 ADR 0008 剩下的「AI 从饭菜照片估算营养」。
- [ADR 0053](./0053-recognition-log-detail.md)：识别明细可点开看详情——每次识别多记入口、服务商、思考参数、图片大小、结束原因、请求 ID、失败原因和模型原文；照片仍不留。
- [ADR 0054](./0054-recognition-price-deepseek.md)：识别详情显示价格——只算 DeepSeek，按北京时间工作日高峰 / 其余空闲（半价）估算；推翻 ADR 0018「不显示费用」在详情页的部分。
- [ADR 0055](./0055-exercise-dataset-and-media.md)：预置动作接入开源动作数据集——1324 个动作经管理端导入，同名老预置只补资料；动图放上传目录、登录才给、不进仓库；App 加动作详情页（动图＋要领）。
- [ADR 0056](./0056-food-search-ranking-and-aliases.md)：食物搜索——字按顺序出现即命中，按匹配度分档，「常用」改看近 30 天记录次数，自建排在预置前面；食物加别名（谁能改名谁能改别名，预置名字方括号里的叫法不搬）。
- [ADR 0057](./0057-food-image.md)：食物图片——每条一张方形封面，只存方图和缩略图；自建的占照片空间、不进相册，预置的由管理员设；删食物时图片跟着进回收站。
- [ADR 0058](./0058-food-search-two-tiers-and-pinyin-initials.md)：食物搜索匹配度缩成两档（完全一样／其余命中），让记录次数和自建说话；加拼音首字母搜索（多音字都算）；推翻 ADR 0056 的四档与「拼音暂不做」。
- [ADR 0059](./0059-water-per-drink-and-entry-time.md)：餐次条目和饮水记时刻（默认记录那一刻，补记按餐次给默认）；饮水从每天一个累计数改成一笔一笔记，删了不进回收站。
- [ADR 0060](./0060-meal-recognition-hint-and-follow-up.md)：记一餐 AI 识别前可给提示词（最近用过的做快捷项、不自动带上），识别后 AI 列出拿不准的问题、可不限轮数地回复纠正；照片留在手机里每轮重发。
- [ADR 0061](./0061-bowel-record.md)：排便记录——大便／小便、时刻、备注、可选布里斯托形态；入口在首页餐次入口的第二页（2026-11-21 从身体页最底部挪来）；有统计，进回收站，外部 agent 可读。
- [ADR 0062](./0062-main-meal-single-time-and-clearable-time.md)：早午晚三餐一餐一个时刻、整餐共用，加餐按条记且可多选批量改；饮水、条目、整餐的时刻都能清除；修订 ADR 0059 决策 1。
- [ADR 0063](./0063-food-per100-truth-cook-state-and-list-row.md)：食物以每 100 为唯一准数（每份只读，推翻按份录入）；加「生重／熟重」；列表行显示三大营养素，自定义用缩略图角标、预置不标；记一餐点「+」才记录、点行看详情，份量可按份填。
- [ADR 0064](./0064-food-order-by-last-used.md)：食物列表的「常用」改看最后一次记进餐里的时间（不限 30 天）；修订 ADR 0056、0058 的档内顺序。
- [ADR 0065](./0065-transparent-status-bar.md)：透明状态栏——页面画到状态栏底下，壳经 `FitShell` 把状态栏高度交给 H5，各页顶部留白统一用 `$safe-top`，顶部垫渐隐带；黑底层把状态栏图标换白。

## 与其他文档的分工

| 要查的东西 | 去哪 |
|------|------|
| 为什么这么定 / 当时考虑过什么 | **本目录**（本文与各条 ADR） |
| 还有什么没定 | `docs/todo/` |
| 某次 AI 会话做到了哪一步 | `docs/worklog/`（只对创建它的那次会话有意义，**不是事实源**） |
