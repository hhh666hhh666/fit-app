-- 排便记录（ADR 0061）：一次大便或小便一行。
--
-- kind：stool＝大便，urine＝小便。
-- occurred_at：几点几分（只到分）。
-- bristol：布里斯托形态 1–7，只有大便可以填，选填。
-- 删了进回收站（trash_item 的 type＝bowel）。
CREATE TABLE `bowel_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '所属用户',
    `kind`        VARCHAR(8)   NOT NULL COMMENT 'stool＝大便，urine＝小便',
    `occurred_at` DATETIME     NOT NULL COMMENT '时刻（只到分）',
    `bristol`     TINYINT      NULL COMMENT '布里斯托形态 1–7（只大便，选填）',
    `note`        VARCHAR(200) NULL COMMENT '备注',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_br_user_time` (`user_id`, `occurred_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '排便记录（ADR 0061）';
