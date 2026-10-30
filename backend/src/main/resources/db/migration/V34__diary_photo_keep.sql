-- 日记图片常驻相册（ADR 0046）：不再有「无引用满 30 天清理」，失去引用的时刻也就不用记了。
ALTER TABLE `diary_photo`
    DROP INDEX `idx_dp_unreferenced`,
    DROP COLUMN `unreferenced_since`;
