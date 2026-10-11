-- 身体档案：体重与体围（胸／腰／臀／手臂）的定期记录（scope #5；CONTEXT「身体档案」）。
-- 一行＝一次测定：体重与四围均可空（记哪项填哪项，至少一项）。
-- 允许同日多条（晨起空腹／练后称重等场景以 note 区分）；展示取「日期倒序＋提交序」。

CREATE TABLE `body_record` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT        NOT NULL COMMENT '所属用户',
    `measured_at` DATE          NOT NULL COMMENT '测定日期',
    `weight`      DECIMAL(5,1)  NULL COMMENT '体重 kg',
    `chest`       DECIMAL(5,1)  NULL COMMENT '胸围 cm',
    `waist`       DECIMAL(5,1)  NULL COMMENT '腰围 cm',
    `hip`         DECIMAL(5,1)  NULL COMMENT '臀围 cm',
    `arm`         DECIMAL(5,1)  NULL COMMENT '臂围 cm',
    `note`        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '备注（如「晨起空腹」「练后称重」）',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_br_user_date` (`user_id`, `measured_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '身体档案记录';
