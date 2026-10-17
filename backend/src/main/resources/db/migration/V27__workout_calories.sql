-- 训练消耗：会话页顶部细条手填（ADR 0029，2026-10-17）。
-- 只有填了才计入当日总消耗；NULL＝未填（与「填 0」区分：0 是明确的「没消耗」）。
ALTER TABLE `workout`
    ADD COLUMN `calories` INT NULL COMMENT '本次训练消耗热量（大卡，手填；NULL=未填）' AFTER `duration_sec`;
