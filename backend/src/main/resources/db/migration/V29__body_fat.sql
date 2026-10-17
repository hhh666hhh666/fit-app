-- 体脂率：作为「一次测定」的第六项，与体重并列（ADR 0030，2026-10-17）。
-- ⚠️ 只记录、不参与任何计算（不进基础代谢、容量、PR、热量缺口）。
-- 1 位小数：体脂秤给的就是 1 位，留 2 位是假精度。
ALTER TABLE `body_record`
    ADD COLUMN `body_fat` DECIMAL(4, 1) NULL COMMENT '体脂率 %（只记录，不参与计算）' AFTER `weight`;
