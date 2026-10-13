-- 食物库：收藏（置顶标记）。
--   语义——用户对食物的个人标记；**收藏者置顶**，同为收藏的再按浏览次数降序（见 FoodService.baseQuery）。
--   说明——预置数据（user_id NULL）为全系统共享行，其收藏标记也是**全局共享**
--          （与 view_count 同口径，自用工具可接受）；用户自建各自独立。
ALTER TABLE `food`
    ADD COLUMN `favorite` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '收藏（0/1）；收藏＝食物库列表置顶' AFTER `view_count`;
