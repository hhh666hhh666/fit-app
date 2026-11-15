-- 食物别名（ADR 0056）与食物图片（ADR 0057）：只加列，不动任何预置行（ADR 0049）。
--
-- aliases：别名用 | 连接（如 肥肠|大肠头），搜索时和名字同等对待；NULL＝没有别名。
--          预置食物名字方括号里的叫法（如「番茄［西红柿］」）照旧留在名字里，不搬进这一列。
-- image_key：食物图片的文件键（随机串）；文件在上传目录的 {userId}/food/ 下，预置食物在 preset/food/ 下。
-- image_bytes：图片占的字节数（方图 ＋ 缩略图），自建食物的算进照片空间。
ALTER TABLE `food`
    ADD COLUMN `aliases`     VARCHAR(200) NULL COMMENT '别名，| 分隔（ADR 0056）' AFTER `name`,
    ADD COLUMN `image_key`   VARCHAR(40)  NULL COMMENT '食物图片文件键；NULL＝没有图片（ADR 0057）' AFTER `portion_grams`,
    ADD COLUMN `image_bytes` INT          NOT NULL DEFAULT 0 COMMENT '食物图片字节数（方图＋缩略图）' AFTER `image_key`;
