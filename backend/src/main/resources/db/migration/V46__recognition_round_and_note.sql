-- 识别前的提示词与多轮纠正（ADR 0060）：用量流水每一轮单独一条，多记两列。
--
-- round：第几轮；记一餐识别里主人每回复一次就多一轮。NULL＝老记录（都是第 1 轮）。
-- note：这一轮主人说的话——第 1 轮是拍照前的提示词，之后是主人的回复；没说就是 NULL。
ALTER TABLE `food_recognition`
    ADD COLUMN `round` INT          NULL COMMENT '第几轮（ADR 0060）；NULL＝老记录，第 1 轮' AFTER `kind`,
    ADD COLUMN `note`  VARCHAR(500) NULL COMMENT '这一轮主人说的话：提示词或回复（ADR 0060）' AFTER `round`;
