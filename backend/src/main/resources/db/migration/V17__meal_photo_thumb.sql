-- 餐照缩略图（ADR 0013）：相册与列表加载小图，点开才拉原图。
--   语义——上传时由**后端**生成长边 ≤320 的 JPEG 存这里；老数据为 NULL，读取时回退用原图。
--   占用统计口径 = size_bytes（原图）＋ LENGTH(thumb)。
ALTER TABLE `meal_photo`
    ADD COLUMN `thumb` MEDIUMBLOB NULL
        COMMENT '缩略图（长边≤320 的 JPEG；上传时后端生成，老数据为空）' AFTER `image`;
