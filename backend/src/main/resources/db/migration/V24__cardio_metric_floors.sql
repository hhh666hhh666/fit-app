-- 有氧动作专属记录口径（ADR 0025）：爬楼按「层数」记，不再走「重量×次数」。
--
-- ① `exercise.metric`：这个动作用哪套记录口径（weight_reps 默认；floors＝层数）。
-- ② `workout_set.floors`：层数本身；只有 metric=floors 的动作会有值。
--
-- ⚠️ 历史爬楼组的层数**无法反推**（当时填在重量或次数栏、口径也未定义），一律留 NULL——
--    页面按「—」显示，宁缺勿错。容量与 PR 早在 V23 就把有氧排除在外，不受影响。

ALTER TABLE `exercise`
    ADD COLUMN `metric` VARCHAR(16) NOT NULL DEFAULT 'weight_reps'
        COMMENT '记录口径：weight_reps=重量×次数（默认）／floors=层数（爬楼）' AFTER `muscle`;

UPDATE `exercise`
SET `metric` = 'floors'
WHERE `muscle` = 'cardio'
  AND `name` = '爬楼';

ALTER TABLE `workout_set`
    ADD COLUMN `floors` INT NULL
        COMMENT '层数（metric=floors 的动作专用；其他动作与历史数据为 NULL）' AFTER `reps`;
