-- 训练计划与周安排：plan（计划）→ plan_exercise（编排，含组次目标）；week_schedule（周表）。
-- 历史一致性：plan_exercise 存 exercise_id（弱引用，动作只下架不物删）＋ name/muscle 快照兜底，
-- 计划编辑页与列表完全不依赖 join 动作库。
-- 删除计划：编排连带删除；周表中引用该计划的格子一并移除（回到「未安排」）。
-- workout 增加 plan_id 弱引用：记录「这次训练由哪个计划发起」，供计划卡显示「上次」；自由训练为 NULL。

CREATE TABLE `plan` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
    `name`       VARCHAR(32) NOT NULL COMMENT '计划名（用户自定义，同用户内不重名）',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_plan_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '训练计划';

CREATE TABLE `plan_exercise` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_id`     BIGINT      NOT NULL COMMENT '所属计划',
    `exercise_id` BIGINT      NOT NULL COMMENT '引用动作库（弱引用，动作只下架不物删）',
    `name`        VARCHAR(64) NOT NULL COMMENT '动作名快照',
    `muscle`      VARCHAR(16) NOT NULL COMMENT '肌群快照（码，见 CONTEXT.md）',
    `target_sets` INT         NULL COMMENT '目标组数（NULL=未设）',
    `target_reps` INT         NULL COMMENT '目标每组次数（NULL=未设）',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '编排顺序',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_pe_plan` (`plan_id`),
    KEY `idx_pe_exercise` (`exercise_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '计划内的动作编排';

CREATE TABLE `week_schedule` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '所属用户',
    `day_of_week` TINYINT  NOT NULL COMMENT '周几（0=周一 … 6=周日）',
    `plan_id`     BIGINT   NULL COMMENT '安排的计划；与 is_rest=1 互斥，二者皆无为「未安排」（无行）',
    `is_rest`     TINYINT  NOT NULL DEFAULT 0 COMMENT '1=休息日',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ws_user_day` (`user_id`, `day_of_week`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '周训练安排（每用户每天至多一行）';

ALTER TABLE `workout`
    ADD COLUMN `plan_id` BIGINT NULL COMMENT '来源计划（弱引用；自由训练为 NULL）' AFTER `user_id`,
    ADD KEY `idx_workout_user_plan` (`user_id`, `plan_id`);
