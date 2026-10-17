-- 计划目标改为「每组各一份」＋ 循环组号（ADR 0027 / ADR 0028，2026-10-17）。
-- plan_exercise_set：一行一组，动作内按 set_index 排序；组数的唯一真相是行数。
-- plan_exercise.target_sets 降级为冗余列（写入时与行数同步，列表页算 EST. VOLUME 用）；
-- target_reps / target_weight 保留，语义改为「新加一组时的默认值」。
-- 回填：把现存计划按 target_sets 展开成同值的 N 行，保证老计划的预填行为不变。

ALTER TABLE `plan_exercise`
    ADD COLUMN `circuit_no` INT NULL COMMENT '循环组号（NULL=不参与循环；同号动作按 sort_order 成组）' AFTER `sort_order`;

CREATE TABLE `plan_exercise_set`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_exercise_id` BIGINT       NOT NULL COMMENT '所属计划动作编排',
    `set_index`        INT          NOT NULL COMMENT '组序号（1 起，动作内连续）',
    `reps`             INT          NULL COMMENT '该组目标次数（NULL=该组未设次数）',
    `weight`           DECIMAL(5, 2) NULL COMMENT '该组目标重量 kg（NULL=该组未设重量）',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_pes_pe` (`plan_exercise_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '计划动作的逐组目标';

INSERT INTO `plan_exercise_set` (`plan_exercise_id`, `set_index`, `reps`, `weight`)
SELECT pe.`id`, n.`n`, pe.`target_reps`, pe.`target_weight`
FROM `plan_exercise` pe
         JOIN (SELECT 1 AS n
               UNION ALL SELECT 2
               UNION ALL SELECT 3
               UNION ALL SELECT 4
               UNION ALL SELECT 5
               UNION ALL SELECT 6
               UNION ALL SELECT 7
               UNION ALL SELECT 8
               UNION ALL SELECT 9
               UNION ALL SELECT 10
               UNION ALL SELECT 11
               UNION ALL SELECT 12
               UNION ALL SELECT 13
               UNION ALL SELECT 14
               UNION ALL SELECT 15
               UNION ALL SELECT 16
               UNION ALL SELECT 17
               UNION ALL SELECT 18
               UNION ALL SELECT 19
               UNION ALL SELECT 20) n
              ON n.n <= pe.`target_sets`;
