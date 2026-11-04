-- 管理端与预置下架（ADR 0048）。
--
-- 三处调整：
--   1) user.admin：管理员标记。界面上没有授予入口，要标谁就手写一条 UPDATE。
--   2) food.archived：预置食物的下架标记，和 exercise.archived 是同一种做法。
--      用户自建的食物照旧物理删除、进回收站，所以这一列对它们恒为 0。
--   3) preset_hidden：用户对一条已下架的预置说「不要了」——只对这个用户隐藏，那一行不动。
--      重新上架时整批清掉（连同回收站里对应的条目）。
--
-- ⚠️ 本迁移起，迁移脚本不再增删改具体的预置行（ADR 0049）：预置数据以数据库为准。
ALTER TABLE `user`
    ADD COLUMN `admin` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '管理员标记（1=能进管理端；只能手写 SQL 授予）' AFTER `password_hash`;

ALTER TABLE `food`
    ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '下架标记（只用于预置：1=没用过的人搜不到，用过的人带「已下架」看到）' AFTER `category`;

CREATE TABLE `preset_hidden` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NOT NULL COMMENT '谁隐藏的',
    `kind`       VARCHAR(16) NOT NULL COMMENT '预置类型：food / exercise',
    `ref_id`     BIGINT      NOT NULL COMMENT '被隐藏的预置行 id（food.id 或 exercise.id）',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '隐藏时刻',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ph_user_kind_ref` (`user_id`, `kind`, `ref_id`),
    KEY `idx_ph_kind_ref` (`kind`, `ref_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户对已下架预置的隐藏（ADR 0048）';
