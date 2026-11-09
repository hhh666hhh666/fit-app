-- 饮食条目补一列钠快照（ADR 0052）：AI 识别出的临时条目要带钠；从食物库记的条目也一并快照。
-- 可空：老条目没有这一列的数据，不回填。
ALTER TABLE `meal_log`
    ADD COLUMN `sodium_per100` DECIMAL(8, 2) NULL COMMENT '钠快照（每100，mg；老条目为空）' AFTER `fat_per100`;
