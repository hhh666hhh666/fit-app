-- 常用份只存单位、条目冻结按份记的单位（ADR 0066）；记一餐列表可以移除吃过的临时条目。

-- 1) 条目记下「按什么单位、一个多少 g」：按份记（含边吃边数，ADR 0067）才有，按克记为 NULL
ALTER TABLE `meal_log`
    ADD COLUMN `portion_unit`  VARCHAR(8)    NULL COMMENT '按份记时的单位快照（如「个」）；按克记为 NULL' AFTER `grams`,
    ADD COLUMN `portion_grams` DECIMAL(7, 2) NULL COMMENT '按份记时一份多少 g 的快照' AFTER `portion_unit`;

ALTER TABLE `food`
    MODIFY COLUMN `portion_label` VARCHAR(16) NULL COMMENT '常用份的单位（如「个」，ADR 0066）';

-- 2) 自建食物的老份标签迁成单位（预置行不动，ADR 0049；显示时去掉开头的 1 / 一）
--    「1个」「一小碗」→ 去掉开头的 1 / 一
UPDATE `food`
SET `portion_label` = TRIM(SUBSTRING(`portion_label`, 2))
WHERE `user_id` IS NOT NULL
  AND `portion_label` REGEXP '^[1一][[:space:]]*[^0-9.[:space:]]';

--    「2片」+ 30g → 「片」+ 15g（先按原标签算克数，再改标签：单表 UPDATE 的赋值从左到右生效）
UPDATE `food`
SET `portion_grams` = ROUND(`portion_grams` / CAST(REGEXP_SUBSTR(`portion_label`, '^[0-9]+') AS UNSIGNED), 2),
    `portion_label` = TRIM(REGEXP_REPLACE(`portion_label`, '^[0-9]+[[:space:]]*', ''))
WHERE `user_id` IS NOT NULL
  AND `portion_grams` IS NOT NULL
  AND `portion_label` REGEXP '^([2-9]|[1-9][0-9]+)[[:space:]]*[^0-9.[:space:]]';

--    其余认不出来的（还带数字、括号、「半」，或太长）→「份」，克数不动
UPDATE `food`
SET `portion_label` = '份'
WHERE `user_id` IS NOT NULL
  AND `portion_label` IS NOT NULL
  AND (`portion_label` REGEXP '[0-9（）()./半]' OR CHAR_LENGTH(`portion_label`) > 4 OR TRIM(`portion_label`) = '');

-- 3) 从记一餐列表里移除的临时条目：按「名字 ＋ 每 100 营养」认一种；移除之后又记了同样的一条，它会重新出现
CREATE TABLE `meal_pick_hidden` (
    `id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`   BIGINT       NOT NULL COMMENT '所属用户',
    `pick_key`  VARCHAR(255) NOT NULL COMMENT '名字与每 100 营养拼成的键（同 MealPickService 的分组键）',
    `hidden_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '移除时刻；之后再记的会重新出现',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pick_hidden` (`user_id`, `pick_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '记一餐列表里移除的临时条目';
