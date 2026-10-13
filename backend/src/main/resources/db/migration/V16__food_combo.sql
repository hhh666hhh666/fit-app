-- 食物组合：一串「食物 ＋ 固定克数」的集合，记录一餐时可整份加入（ADR 0012）。
--   语义——组合是"批量添加的快捷键"：加入时展开为多条普通 meal_log，不新开记录类型。
--   明细行对 food 是**弱引用**（食物删除后置空，展示靠 food_name 快照）；
--   组合**不存营养快照**——整份热量由服务端 JOIN food 现算，食物已被删的条目不计入。
CREATE TABLE `food_combo` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
    `name`       VARCHAR(64) NOT NULL COMMENT '组合名（用户自定）',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_fc_user` (`user_id`, `name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '食物组合（一串食物 ＋ 固定克数；记录一餐时整份加入）';

CREATE TABLE `food_combo_item` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `combo_id`   BIGINT        NOT NULL COMMENT '所属组合',
    `food_id`    BIGINT        NULL COMMENT '来源食物（弱引用；删除后置空，展示靠快照）',
    `food_name`  VARCHAR(64)   NOT NULL COMMENT '食物名快照',
    `grams`      DECIMAL(7, 2) NOT NULL COMMENT '组合里的固定克数（ml 单位食物为毫升）',
    `sort_order` INT           NOT NULL DEFAULT 0 COMMENT '组内顺序（加入一餐时按此顺序落库）',
    PRIMARY KEY (`id`),
    KEY `idx_fci_combo` (`combo_id`, `sort_order`),
    KEY `idx_fci_food` (`food_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '食物组合明细行';
