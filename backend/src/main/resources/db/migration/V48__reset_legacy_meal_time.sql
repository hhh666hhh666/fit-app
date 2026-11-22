-- 0.40.0 之前保存的餐次条目，时刻一律改成默认值（ADR 0059 修订）。
--
-- V45 给老条目回填了「保存时的钟点」，但那时候多半是吃完很久才补记的，钟点不可信（主人 2026-11-21 反馈）。
-- 早餐 8:00、午餐 12:00、晚餐 18:00——和补记别的日子时的默认值同一套；
-- 加餐没有固定的默认钟点，清空（界面上不显示时刻），不编一个出来。
-- 「老条目」＝ V45 装上之前保存的：V45 之后的条目时刻都是记的时候确认过的，不动。
UPDATE `meal_log`
SET `eaten_at` = CASE `meal`
                     WHEN 'breakfast' THEN '08:00:00'
                     WHEN 'lunch' THEN '12:00:00'
                     WHEN 'dinner' THEN '18:00:00'
                     ELSE NULL
    END
WHERE `created_at` < (SELECT `installed_on` FROM `flyway_schema_history` WHERE `version` = '45');
