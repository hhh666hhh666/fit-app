-- 头像与历史头像（ADR 0051）：每张都留原图，另存缩略图、预览图和 512px 的裁剪结果。
--   文件都在 {photo-dir}/{path}.*：原图 .{ext}、缩略图 .thumb.jpg、预览图 .view.jpg、裁剪结果 .crop.jpg。
--   坐标与宽高都按 EXIF 方向**摆正之后**算——和浏览器里看到的方向一致。
--   历史按 used_at 倒序：重新裁剪也会把它刷新成「现在」，于是这一项移到最前面，不另起一条。
CREATE TABLE `avatar` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '所属用户',
    `path`        VARCHAR(128) NOT NULL COMMENT '相对路径（不含扩展名）：{userId}/avatar/{随机串}',
    `mime`        VARCHAR(32)  NOT NULL COMMENT '原图类型',
    `width`       INT          NOT NULL COMMENT '摆正后的宽（px）',
    `height`      INT          NOT NULL COMMENT '摆正后的高（px）',
    `size_bytes`  INT          NOT NULL COMMENT '原图字节',
    `extra_bytes` INT          NOT NULL DEFAULT 0 COMMENT '缩略图＋预览图＋裁剪结果的字节',
    `crop_x`      INT          NOT NULL COMMENT '裁剪框左上角 x（摆正后的像素）',
    `crop_y`      INT          NOT NULL COMMENT '裁剪框左上角 y',
    `crop_size`   INT          NOT NULL COMMENT '裁剪框边长（正方形）',
    `used_at`     DATETIME     NOT NULL COMMENT '最近一次设为当前头像的时刻',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时刻',
    PRIMARY KEY (`id`),
    KEY `idx_avatar_user_used` (`user_id`, `used_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '头像（含历史头像）';

ALTER TABLE `user`
    ADD COLUMN `avatar_id` BIGINT NULL COMMENT '当前头像（avatar.id）；NULL＝显示首字母' AFTER `nickname`;
