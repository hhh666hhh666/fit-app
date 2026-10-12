-- 饮食记录（nutrition）：food（食物库）/ meal_log（餐次条目）/ meal_photo（每餐一张照片）/
-- diet_daily（每日饮水累计）/ user_setting（设置与目标）。
-- 口径见 ADR 0008；营养字段全集（45 项）见 docs/design/食物营养字段参考.md——4+1 必填、其余可空。
-- 快照语义：meal_log 存「每 100 数值快照 + 实际克数」，改库/删库不影响历史与趋势；
-- food_id 为弱引用，删除食物后不物删条目（展示靠 food_name 快照自足）。

CREATE TABLE `food` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT        NOT NULL COMMENT '所属用户',
    `name`            VARCHAR(64)   NOT NULL COMMENT '食物名（同用户内唯一）',
    `unit`            VARCHAR(8)    NOT NULL DEFAULT 'g' COMMENT '基准单位：g=每100g / ml=每100ml',
    -- 主组（4+1 必填）：能量/蛋白/脂肪/碳水/钠
    `kcal`            DECIMAL(7, 2) NOT NULL COMMENT '能量（大卡）',
    `protein`         DECIMAL(6, 2) NOT NULL COMMENT '蛋白质（g）',
    `fat`             DECIMAL(6, 2) NOT NULL COMMENT '脂肪（g）',
    `carb`            DECIMAL(6, 2) NOT NULL COMMENT '碳水化合物（g）',
    `sodium`          DECIMAL(8, 2) NOT NULL COMMENT '钠（mg）',
    `saturated_fat`   DECIMAL(6, 2) NULL COMMENT '饱和脂肪酸（g）',
    `sugar`           DECIMAL(6, 2) NULL COMMENT '糖（g）',
    -- 脂类相关（选填）
    `trans_fat`       DECIMAL(10, 2) NULL COMMENT '反式脂肪酸（g）',
    `mono_fat`        DECIMAL(10, 2) NULL COMMENT '单不饱和脂肪酸（g）',
    `poly_fat`        DECIMAL(10, 2) NULL COMMENT '多不饱和脂肪酸（g）',
    `cholesterol`     DECIMAL(10, 2) NULL COMMENT '胆固醇（mg）',
    -- 碳水相关（选填）
    `fiber`           DECIMAL(10, 2) NULL COMMENT '膳食纤维（g）',
    `soluble_fiber`   DECIMAL(10, 2) NULL COMMENT '可溶性膳食纤维（g）',
    `insoluble_fiber` DECIMAL(10, 2) NULL COMMENT '不溶性膳食纤维（g）',
    `starch`          DECIMAL(10, 2) NULL COMMENT '淀粉（g）',
    `lactose`         DECIMAL(10, 2) NULL COMMENT '乳糖（g）',
    -- 维生素（选填）
    `va`              DECIMAL(10, 2) NULL COMMENT '维生素A（μg RE）',
    `vd`              DECIMAL(10, 2) NULL COMMENT '维生素D（μg）',
    `ve`              DECIMAL(10, 2) NULL COMMENT '维生素E（mg α-TE）',
    `vb1`             DECIMAL(10, 2) NULL COMMENT '维生素B1（mg）',
    `vb2`             DECIMAL(10, 2) NULL COMMENT '维生素B2（mg）',
    `vb6`             DECIMAL(10, 2) NULL COMMENT '维生素B6（mg）',
    `vb12`            DECIMAL(10, 2) NULL COMMENT '维生素B12（μg）',
    `vc`              DECIMAL(10, 2) NULL COMMENT '维生素C（mg）',
    `niacin`          DECIMAL(10, 2) NULL COMMENT '烟酸（mg）',
    `folate`          DECIMAL(10, 2) NULL COMMENT '叶酸（μg DFE）',
    `pantothenic`     DECIMAL(10, 2) NULL COMMENT '泛酸（mg）',
    `biotin`          DECIMAL(10, 2) NULL COMMENT '生物素（μg）',
    -- 矿物质（选填）
    `calcium`         DECIMAL(10, 2) NULL COMMENT '钙（mg）',
    `phosphorus`      DECIMAL(10, 2) NULL COMMENT '磷（mg）',
    `potassium`       DECIMAL(10, 2) NULL COMMENT '钾（mg）',
    `magnesium`       DECIMAL(10, 2) NULL COMMENT '镁（mg）',
    `iron`            DECIMAL(10, 2) NULL COMMENT '铁（mg）',
    `zinc`            DECIMAL(10, 2) NULL COMMENT '锌（mg）',
    `iodine`          DECIMAL(10, 2) NULL COMMENT '碘（μg）',
    `selenium`        DECIMAL(10, 2) NULL COMMENT '硒（μg）',
    `copper`          DECIMAL(10, 2) NULL COMMENT '铜（mg）',
    `manganese`       DECIMAL(10, 2) NULL COMMENT '锰（mg）',
    `fluoride`        DECIMAL(10, 2) NULL COMMENT '氟（mg）',
    `chromium`        DECIMAL(10, 2) NULL COMMENT '铬（μg）',
    `molybdenum`      DECIMAL(10, 2) NULL COMMENT '钼（μg）',
    -- 理化（选填）
    `water`           DECIMAL(10, 2) NULL COMMENT '水分（g）',
    `ash`             DECIMAL(10, 2) NULL COMMENT '灰分（g）',
    `alcohol`         DECIMAL(10, 2) NULL COMMENT '乙醇（g）',
    `organic_acid`    DECIMAL(10, 2) NULL COMMENT '有机酸（g）',
    -- 常用份（可选）
    `portion_label`   VARCHAR(16)   NULL COMMENT '常用份标签（如「1个」）',
    `portion_grams`   DECIMAL(7, 2) NULL COMMENT '常用份克重（如 50）',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_food_user_name` (`user_id`, `name`),
    KEY `idx_food_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '食物库（用户自建）';

CREATE TABLE `meal_log` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT        NOT NULL COMMENT '所属用户',
    `log_date`       DATE          NOT NULL COMMENT '记录日期',
    `meal`           VARCHAR(16)   NOT NULL COMMENT '餐次：breakfast/lunch/dinner/snack',
    `food_id`        BIGINT        NULL COMMENT '来源食物（弱引用；删除后置空，展示靠快照）',
    `food_name`      VARCHAR(64)   NOT NULL COMMENT '食物名快照',
    `grams`          DECIMAL(7, 2) NOT NULL COMMENT '实际摄入量（克；ml 单位食物为毫升）',
    `kcal_per100`    DECIMAL(7, 2) NOT NULL COMMENT '热量快照（每100）',
    `protein_per100` DECIMAL(6, 2) NOT NULL COMMENT '蛋白快照（每100，g）',
    `carb_per100`    DECIMAL(6, 2) NOT NULL COMMENT '碳水快照（每100，g）',
    `fat_per100`     DECIMAL(6, 2) NOT NULL COMMENT '脂肪快照（每100，g）',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ml_user_date` (`user_id`, `log_date`),
    KEY `idx_ml_food` (`food_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '饮食条目（某日某餐的一条食物记录；营养按当时快照）';

CREATE TABLE `meal_photo` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
    `log_date`   DATE        NOT NULL COMMENT '日期',
    `meal`       VARCHAR(16) NOT NULL COMMENT '餐次',
    `mime`       VARCHAR(32) NOT NULL COMMENT '图片类型（image/jpeg 等）',
    `size_bytes` INT         NOT NULL COMMENT '字节数',
    `image`      MEDIUMBLOB  NOT NULL COMMENT '图片数据（前端已压缩）',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mp_user_date_meal` (`user_id`, `log_date`, `meal`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '每餐一张照片（重拍即替换）';

CREATE TABLE `diet_daily` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT   NOT NULL COMMENT '所属用户',
    `log_date`   DATE     NOT NULL COMMENT '日期',
    `water_ml`   INT      NOT NULL DEFAULT 0 COMMENT '当日饮水累计（ml）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dd_user_date` (`user_id`, `log_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '每日饮水累计（每用户每天至多一行）';

CREATE TABLE `user_setting` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT        NOT NULL COMMENT '所属用户',
    `photo_quality`  VARCHAR(16)   NOT NULL DEFAULT 'medium' COMMENT '图片压缩：lossless/high/medium/low',
    `water_step_ml`  INT           NOT NULL DEFAULT 250 COMMENT '饮水步进（ml）',
    `target_kcal`    DECIMAL(7, 2) NULL COMMENT '每日热量目标（大卡）',
    `target_protein` DECIMAL(7, 2) NULL COMMENT '每日蛋白目标（g）',
    `target_carb`    DECIMAL(7, 2) NULL COMMENT '每日碳水目标（g）',
    `target_fat`     DECIMAL(7, 2) NULL COMMENT '每日脂肪目标（g）',
    `target_water`   INT           NULL COMMENT '每日饮水目标（ml）',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_us_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '饮食设置与目标（每用户一行；行不存在时读默认值）';
