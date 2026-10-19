-- 计划目标支持「逐组层数」（ADR 0031，2026-10-19）。
-- 有氧里只有爬楼有专属口径（metric = floors，ADR 0025）：记录侧的层数在 workout_set.floors，
-- 计划侧原本只存得下组数，于是「3 组各爬 20 / 20 / 15 层」在计划里根本写不出来。
-- 与 workout_set 同构：单独一列，不复用 reps（复用会让同一个数在两套语义里混用，ADR 0025 已否过）。

ALTER TABLE `plan_exercise_set`
    ADD COLUMN `floors` INT NULL COMMENT '该组目标层数（仅 metric=floors 的动作；NULL=该组未设层数）' AFTER `weight`;
