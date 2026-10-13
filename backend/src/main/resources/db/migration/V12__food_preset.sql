-- 食物库引入系统预置数据（来源：《中国食物成分表》等），与动作库 exercise 同一范式：
--   user_id = NULL → 系统预置（只读，用户可用「复制为自定义」建自己的副本）。
--
-- 三处调整：
--   1) user_id 放宽可空——预置数据不属于任何用户；uk_food_user_name 对含 NULL 的行不生效
--      （MySQL 特性），预设之间允许同名（国标数据本身有重名条目，如 5 条「栗子［板栗］」）。
--   2) sodium 放宽可空——预置数据按国标表录入，部分条目钠未测（'—'），以 NULL 表示未测；
--      「用户自建 4+1 必填」的规矩不变（校验在 FoodSaveDTO，与列可空性无关）。
--   3) 新增 category——承载《中国食物成分表》的分类（如「谷类及其制品-稻米」「鱼虾蟹贝类-鱼」）；
--      仅预置数据使用，用户自建为 NULL。
ALTER TABLE `food`
    MODIFY COLUMN `user_id` BIGINT        NULL COMMENT '所属用户（NULL=系统预置，与 exercise 同范式）',
    MODIFY COLUMN `sodium`  DECIMAL(8, 2) NULL COMMENT '钠（mg；NULL=未测，预置数据可能出现）',
    ADD COLUMN `category`   VARCHAR(32)   NULL COMMENT '分类（预置=国标大类；用户自建=NULL）' AFTER `unit`;
