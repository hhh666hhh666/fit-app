-- 食物搜索的拼音首字母（ADR 0058）：只加列，不动任何预置行（ADR 0049）。
--
-- initials：名字（按 ［ ］ 、 ， 切成几段）和每个别名的拼音首字母，各段用 | 隔开、两头也带 |，
--           多音字的每种读法都列出来（如「重庆」→ |zq|cq|）。由应用在写名字、别名时算好；
--           已有的行在应用启动时补算（FoodInitialsBackfill），NULL＝还没算。
ALTER TABLE `food`
    ADD COLUMN `initials` VARCHAR(1000) NULL COMMENT '名字与别名的拼音首字母，| 分隔（ADR 0058）' AFTER `aliases`;
