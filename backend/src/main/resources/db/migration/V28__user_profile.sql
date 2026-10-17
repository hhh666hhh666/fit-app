-- 个人档案：身高／性别／出生日期／生活活动档，一个用户一份（ADR 0030，2026-10-17）。
-- 与「身体档案」（body_record，体重体脂围度的测定序列）是两件事：这里存基本不变的信息。
-- 体重不进这张表——它是会变的东西，仍取 body_record 最新一条。
CREATE TABLE `user_profile`
(
    `user_id`        BIGINT       NOT NULL COMMENT '主键＝用户（一个用户一份档案）',
    `sex`            VARCHAR(8)   NOT NULL COMMENT '性别：male / female',
    `birth_date`     DATE         NOT NULL COMMENT '出生日期（年龄每次现算，不存年龄数字）',
    `height_cm`      DECIMAL(5, 1) NOT NULL COMMENT '身高 cm',
    `activity_level` VARCHAR(16)  NOT NULL COMMENT '生活活动档：sedentary / light / active（只含日常生活，不含训练）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '个人档案（基本不变的身体基础信息）';
