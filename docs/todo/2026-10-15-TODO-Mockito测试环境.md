# 待办：Mockito 测试环境

创建：2026-10-15 · 状态：待办 · 优先级：低

## 背景
本机跑 `mvn test` 时间歇性全部报错：Mockito inline mock-maker 初始化失败，根因是 Windows 上 byte-buddy agent 自附着（external process attach）偶发失败，与业务代码无关（未改动的 AuthServiceTest 同样中招）。2026-10-15 实测三次里两次失败、一次全绿（127 用例）；CI 的 ubuntu runner 上未复现过。

## 可选方案
- **方案甲**：按 Mockito 官方建议把 byte-buddy-agent 配成 Surefire 的 `-javaagent` —— 代价：要调整 Maven 参数并验证 CI 与本地一致，且 jar 路径解析与版本耦合。
- **方案乙**：只在文档里记下「失败先重跑再排查」 —— 代价：每次偶发仍要花约 1 分钟才能判断是不是真失败。

➡️ 倾向：暂定方案乙，成本最低；若复发频率变高再上方案甲。

## 待确认
无。

## 更新记录
- 2026-10-15：记录本机偶发失败现象与根因（byte-buddy 自附着间歇失败），复跑确认可恢复，优先级从中降为低。
