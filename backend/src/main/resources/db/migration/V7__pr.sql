-- PR 标记：提交训练时逐组对比该动作历史（weight×reps），刷新历史最佳则置 1。
-- 历史布尔（「当时是 PR」）一经写入不再回改；「当前各动作最佳」由统计接口按 MAX(weight×reps) 现算，不依赖本列。
-- 仅 exercise_id 非空的组参与判定（自由训练未挂动作库的组无从对比）。

ALTER TABLE `workout_set`
    ADD COLUMN `pr` TINYINT NOT NULL DEFAULT 0 COMMENT '提交时是否刷新该动作历史最佳（weight×reps）' AFTER `reps`;
