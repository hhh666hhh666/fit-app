-- 身体体重改为 kg 两位小数，支持斤/千克展示切换后的精确换算。
ALTER TABLE `body_record`
    MODIFY COLUMN `weight` DECIMAL(6,2) NULL COMMENT '体重 kg';
