---
target: 全站弹窗
total_score: 23
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Users\\lzh\\storage\\program\\claude\\fit-app\\frontend\\src\\components\\FtSheet.vue"
target_fingerprint: "sha256:d24b2afdf39cef8c3baf83c9c5daf7c3c2bf31523a6dcdcdff7174a52f59ecdf"
target_path: "C:\\Users\\lzh\\storage\\program\\claude\\fit-app\\frontend\\src\\components\\FtSheet.vue"
timestamp: 2026-10-24T08-50-44Z
slug: frontend-src-components-ftsheet-vue
---
⚠️ DEGRADED: single-context（会话规则不允许自行启动子代理）

范围：全站弹窗——FtSheet（10 处）、MealSheet、ComboEditSheet、底栏选计划弹层、PhotoViewer、uni.showModal（17）、showActionSheet（5）、showToast（约 80）。

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|---|---|---|
| 1 | 系统状态可见 | 3 | 保存／删除都有轻提示；成功提示有勾无勾不一 |
| 2 | 贴合真实世界 | 3 | 确认文案把后果说清（「历史记录不受影响」） |
| 3 | 用户控制与自由 | 1 | 点遮罩丢表单；安卓返回键不关弹层；删除无撤销 |
| 4 | 一致性 | 1 | 三套皮；删除标红 6/10；关闭钮只有部分弹层有 |
| 5 | 防错 | 2 | 「开新的」一键清会话；饮食条目、已完成组删除不确认 |
| 6 | 识别优于回忆 | 3 | 选计划置顶「今天 · 建议」好；训练安排菜单不标当前 |
| 7 | 灵活高效 | 3 | 轻提示不打断；长按菜单藏得深 |
| 8 | 美观简约 | 2 | uni 默认皮 3px 圆角、iOS 蓝、纯黑遮罩，与玻璃语言脱节 |
| 9 | 错误恢复 | 2 | 长校验文案只闪 1.5 秒、不指向字段 |
| 10 | 帮助 | 3 | 「怎么算的」弹层、确认里写明影响范围 |
| **Total** | | **23/40** | 中等 |

## Priority Issues

1. [P1] 「开新的」在取消位、一键清掉进行中的训练（FtTabBar.vue:191、plans/index.vue:94，代码重复）。
2. [P1] 安卓返回键不关弹层（MainActivity.onBackPressed 直接 goBack，H5 无拦截）。
3. [P2] uni 默认确认框／操作菜单／轻提示与晴窗脱节，确认框正文 #999 对比度约 2.8:1。
4. [P2] 删除确认口径不一：标红 6／不标红 4；饮食条目删除、会话里删已完成组不确认，而「取消完成」要确认。
5. [P2] FtSheet 点遮罩即丢表单；无关闭钮、无退出动画、把手不能拖、Esc 不关。

## Minor

成功提示带勾 9 处、其余纯文字；长校验文案 1.5 秒；训练安排菜单不标当前、计划多时很长；FtSheet 无 aria-modal、不锁背后滚动。

检测器：五个弹层组件 0 命中。
