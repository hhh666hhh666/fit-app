-- 训练记录三张表：一次训练（workout）→ 动作条目（workout_block）→ 组次（workout_set）。
-- 历史一致性：block 存 exercise_id（弱引用，动作不物删保证可 join）＋ exercise_name 快照兜底；
-- workout.title 为当次标题快照（计划名或「自由训练」），计划区改名/删除不影响历史。

CREATE TABLE `workout` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT        NOT NULL COMMENT '所属用户',
    `title`        VARCHAR(64)   NOT NULL COMMENT '当次标题快照（计划名或「自由训练」）',
    `started_at`   DATETIME      NULL COMMENT '开始时间',
    `duration_sec` INT           NOT NULL DEFAULT 0 COMMENT '训练时长（秒）',
    `total_volume` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '总容量 kg（已完成组 重量×次数 之和）',
    `note`         VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '备注',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_workout_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '一次训练';

CREATE TABLE `workout_block` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workout_id`    BIGINT      NOT NULL COMMENT '所属训练',
    `exercise_id`   BIGINT      NULL COMMENT '引用动作库（弱引用）',
    `exercise_name` VARCHAR(64) NOT NULL COMMENT '动作名快照（兜底显示）',
    `sort_order`    INT         NOT NULL DEFAULT 0 COMMENT '在当次训练中的顺序',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_block_workout` (`workout_id`),
    KEY `idx_block_exercise` (`exercise_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '训练中的动作条目';

CREATE TABLE `workout_set` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `block_id`   BIGINT       NOT NULL COMMENT '所属动作条目',
    `set_index`  INT          NOT NULL COMMENT '第几组（1 起）',
    `weight`     DECIMAL(6,2) NOT NULL COMMENT '重量 kg',
    `reps`       INT          NOT NULL COMMENT '次数',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_set_block` (`block_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '组次';
