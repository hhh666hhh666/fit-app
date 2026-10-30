-- 回收站（ADR 0047）：删除时把整棵数据存成 JSON 快照，原表照常物理删除，保留 30 天。
-- 照片文件留在原处；photo_bytes 记下它们占的空间，继续计入配额。
-- dedupe_key 只给「开新的」丢掉的训练用：本机补传可能重复，靠它只留一条。
CREATE TABLE `trash_item` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '所属用户',
    `type`        VARCHAR(16)  NOT NULL COMMENT '类型：workout/diary/meal_photo/diary_photo/body/plan/exercise/food/session',
    `source_id`   BIGINT       NULL COMMENT '原来那一行的 id；丢掉的训练没有',
    `title`       VARCHAR(64)  NOT NULL COMMENT '列表标题',
    `summary`     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '列表摘要',
    `snapshot`    MEDIUMTEXT   NOT NULL COMMENT '恢复用的 JSON 快照',
    `photo_bytes` BIGINT       NOT NULL DEFAULT 0 COMMENT '快照带着的照片占用（原图 ＋ 缩略图）',
    `dedupe_key`  VARCHAR(64)  NULL COMMENT '防重复补传',
    `deleted_at`  DATETIME     NOT NULL COMMENT '进回收站的时刻',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trash_user_dedupe` (`user_id`, `dedupe_key`),
    KEY `idx_trash_user_time` (`user_id`, `deleted_at`),
    KEY `idx_trash_deleted_at` (`deleted_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '回收站';
