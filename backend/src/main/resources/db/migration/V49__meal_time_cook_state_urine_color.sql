-- ① 早午晚三餐一餐一个时刻（ADR 0062）：同一天同一餐各条时刻不一样的，统一成最早的那个；
--    整餐都没有时刻的，MIN 是 NULL，保持 NULL。加餐按条记，不动。
UPDATE `meal_log` m
    JOIN (SELECT `user_id`, `log_date`, `meal`, MIN(`eaten_at`) AS `t`
          FROM `meal_log`
          WHERE `meal` IN ('breakfast', 'lunch', 'dinner')
          GROUP BY `user_id`, `log_date`, `meal`) g
    ON m.`user_id` = g.`user_id` AND m.`log_date` = g.`log_date` AND m.`meal` = g.`meal`
SET m.`eaten_at` = g.`t`
WHERE m.`meal` IN ('breakfast', 'lunch', 'dinner')
  AND NOT (m.`eaten_at` <=> g.`t`);

-- ② 食物的生重／熟重（ADR 0063）：raw＝生重，cooked＝熟重，NULL＝没标（现有食物一律不标）。
ALTER TABLE `food`
    ADD COLUMN `cook_state` VARCHAR(8) NULL COMMENT '营养数按生重(raw)还是熟重(cooked)测的；NULL＝没标（ADR 0063）' AFTER `portion_grams`;

-- ③ 小便颜色（ADR 0061 修订）：1–7 依次是无色透明、浅黄、黄、深黄、琥珀、茶褐、红／粉；只小便，选填。
ALTER TABLE `bowel_record`
    ADD COLUMN `color` TINYINT NULL COMMENT '小便颜色 1–7（只小便，选填）' AFTER `bristol`;
