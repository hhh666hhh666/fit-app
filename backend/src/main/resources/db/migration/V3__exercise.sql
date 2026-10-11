-- 动作库：预设动作（user_id 为 NULL）＋ 用户自定义动作。
-- 口径：被历史记录引用的动作不物理删除——删除 = archived 置 1（从可选列表下架），历史照常显示。
-- 肌群码：chest / back / leg / shoulder / arm / core / cardio（前端映射中文显示）。
CREATE TABLE `exercise` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NULL COMMENT 'NULL=预设动作；否则为所属用户 id',
    `name`       VARCHAR(64) NOT NULL COMMENT '动作名',
    `muscle`     VARCHAR(16) NOT NULL COMMENT '肌群码',
    `archived`   TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '下架标记：1=从可选列表隐藏（历史仍显示）',
    `sort_order` INT         NOT NULL DEFAULT 0 COMMENT '排序（预设动作用）',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_exercise_user_muscle` (`user_id`, `muscle`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '动作库';
