-- 餐照改为一餐多张（ADR 0045）：去掉唯一键，同一餐的照片按上传先后排序。
ALTER TABLE `meal_photo`
    DROP INDEX `uk_mp_user_date_meal`,
    ADD KEY `idx_mp_user_date_meal` (`user_id`, `log_date`, `meal`),
    COMMENT = '餐照（一餐可以有多张）';
