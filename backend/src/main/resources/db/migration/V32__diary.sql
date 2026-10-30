-- 日记（ADR 0044）：一篇＝时刻 ＋ 可选标题 ＋ Markdown 正文，一天可写多篇。
CREATE TABLE `diary_entry` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
    `written_at` DATETIME     NOT NULL COMMENT '这篇的时刻（精确到分，可改成过去）',
    `title`      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '可选标题',
    `content`    MEDIUMTEXT   NOT NULL COMMENT 'Markdown 原文（上限 2 万字，含 emoji 时超出 TEXT 的 64 KB）',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_de_user_time` (`user_id`, `written_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '日记';

-- 日记图片：图片实体在文件系统，这里只留相对路径与元数据（同 meal_photo，ADR 0026）。
CREATE TABLE `diary_photo` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`            BIGINT       NOT NULL COMMENT '所属用户',
    `mime`               VARCHAR(32)  NOT NULL COMMENT '图片类型',
    `size_bytes`         INT          NOT NULL COMMENT '原图字节数',
    `thumb_bytes`        INT          NULL COMMENT '缩略图字节数',
    `path`               VARCHAR(128) NOT NULL COMMENT '相对路径（不含扩展名）',
    `unreferenced_since` DATETIME     NULL COMMENT '从何时起没有任何日记引用；为空＝正被引用。满 30 天清理',
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (`id`),
    KEY `idx_dp_user_time` (`user_id`, `created_at`),
    KEY `idx_dp_unreferenced` (`unreferenced_since`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '日记图片';

-- 哪篇日记引用了哪张图：保存时按正文同步。
CREATE TABLE `diary_photo_ref` (
    `entry_id` BIGINT NOT NULL COMMENT '日记',
    `photo_id` BIGINT NOT NULL COMMENT '图片',
    PRIMARY KEY (`entry_id`, `photo_id`),
    KEY `idx_dpr_photo` (`photo_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '日记与图片的引用关系';
