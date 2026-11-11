-- 动作库接入开源动作数据集（ADR 0055）：给动作表加「动作资料」几列，全部可空、老数据不回填。
-- 按 ADR 0049，本脚本只加列，不增删改任何预置行——数据从管理端「导入预置动作」写入。
--
-- source_id：数据集里的编号（如 0025），导入时靠它认出「上次导入过的那条」；同一编号只能对应一行。
-- media：动图与缩略图的文件键（如 0025-EIeI8Vf），文件在上传目录的 exercise-media/ 下，不进库。
ALTER TABLE `exercise`
    ADD COLUMN `source_id` VARCHAR(8)   NULL COMMENT '开源动作数据集编号（导入时据此合并）' AFTER `metric`,
    ADD COLUMN `name_en`   VARCHAR(96)  NULL COMMENT '英文名（来自数据集，搜索也匹配它）' AFTER `source_id`,
    ADD COLUMN `equipment` VARCHAR(32)  NULL COMMENT '器械（中文）' AFTER `name_en`,
    ADD COLUMN `target`    VARCHAR(32)  NULL COMMENT '主要目标肌（中文）' AFTER `equipment`,
    ADD COLUMN `secondary` VARCHAR(128) NULL COMMENT '辅助肌群（中文，顿号分隔）' AFTER `target`,
    ADD COLUMN `steps`     TEXT         NULL COMMENT '动作要领：每步一行' AFTER `secondary`,
    ADD COLUMN `media`     VARCHAR(32)  NULL COMMENT '动图文件键：exercise-media/{media}.gif 与 .jpg' AFTER `steps`,
    ADD UNIQUE KEY `uk_exercise_source` (`source_id`);
