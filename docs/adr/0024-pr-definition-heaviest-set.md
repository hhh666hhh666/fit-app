# PR 判定改为「最重一组」

Status: accepted

PR 判定自 **V7** 起落库为 `workout_set.pr` 标记，判定式是 `MAX(weight × reps)`——即**单组「容量」最大**。这等于让 [ADR 0023](./0023-volume-metric-scope-and-display.md) 讲的容量口径外溢到了 PR：60kg × 20（1200）会压过 100kg × 5（500），于是「个人最好成绩」归档里出现的不是主人心里那个「最重一组」。而 `CONTEXT.md` 与 `docs/requirements/scope.md` #7 写的一直是「历史最重一组（重量×次数）」——文字两可、代码实测取前者，本卡把口径收敛到「重量优先」。

## Considered Options

- **保持单组容量最大**：否——轻重量高次数会抢走 PR 标记，与「最重一组」的文字口径不符，也削弱 PR 作为「推着人加重量」那个数字的价值（`PRODUCT.md` 原则 2）。
- **改成估算 1RM（Epley 等公式）**：否——scope #7 已明确不做估算 1RM；引入公式徒增解释成本，且自用场景不需要。
- **改成「总容量最大」**：否——要先按动作聚合再比，口径更绕，同样偏离「最重一组」的心智。
- **重量优先：取该动作历史最大的 `weight`，同重量再比 `reps`**（选定）

## Consequences

- 历史基线查询由 `MAX(ws.weight * ws.reps)` 改为「按 `weight DESC, reps DESC` 取该动作历史第一组」；服务端 `WorkoutService.writeBlocks` 内逐组递进的判定同步改；编辑记录时的「排除本记录自身」基线（`selectMaxSetVolumeExcluding`）同口径。
- **已落库的 `pr` 标记是按旧口径打出来的**，与新定义可能不一致 → 需要一次性重算（自用数据量小，一条 `UPDATE ... JOIN` 即可）。**是否重算尚未拍板**，见 [改造待办](../todo/archive/2026-10-16-TODO-容量与PR口径改造.md)。
- 统计页 PR 归档、详情页与汇总页的 PR 高亮都会随口径变化；`docs/design/API.md` 里 `ExerciseMaxVO` 之类的口径注释需同步。
- 同一重量下比次数，意味着「100kg × 5」会输给「100kg × 6」——与「最重一组」的心智一致。

> 关联：容量口径 → [ADR 0023](./0023-volume-metric-scope-and-display.md)。

> ⚠️ **后续（2026-10-16 讨论补定）**：
>
> - **有氧动作（`muscle = cardio`）一并排除出 PR 判定**——口径与容量统一（[ADR 0023](./0023-volume-metric-scope-and-display.md)）；不排除则「无历史即为 PR」会把有氧第一次练标成 PR。
> - 上面「是否重算尚未拍板」一条**已拍板**：历史 `pr` 标记**按时间序回放重算**——逐组判定「此前是否存在更优的（重量, 次数）」，保住「破纪录时刻」语义；被否的「只标当前最优那一组」会让历史高亮全部塌掉、「旧标记不动」会让新旧口径混在同一份数据里。
