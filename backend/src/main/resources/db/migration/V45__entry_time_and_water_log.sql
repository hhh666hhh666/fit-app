-- 餐次条目和饮水记时刻；饮水改成一笔一笔记（ADR 0059）。
--
-- meal_log.eaten_at：几点几分吃的（只到分，秒恒为 0）。老条目能推断的才回填——保存时间和记录日期是同一天的，
--                    取保存的钟点；补记的（不是同一天）留 NULL，界面上不显示。库的时区是 +08:00，created_at 就是北京时间。
-- water_log：一笔饮水一行，毫升数 ＋ 时刻，是饮水的真相；diet_daily.water_ml 降为「当天各笔之和」的缓存，
--            由应用在每次增删改一笔时同一事务里重算（趋势、外部 agent 照旧读它）。
--            旧的每天累计数迁成一笔，时刻留 NULL。
ALTER TABLE `meal_log`
    ADD COLUMN `eaten_at` TIME NULL COMMENT '时刻（几点几分吃的，ADR 0059）；NULL＝老条目没法推断' AFTER `meal`;

UPDATE `meal_log`
SET `eaten_at` = MAKETIME(HOUR(`created_at`), MINUTE(`created_at`), 0)
WHERE DATE(`created_at`) = `log_date`;

CREATE TABLE `water_log` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT   NOT NULL COMMENT '所属用户',
    `log_date`   DATE     NOT NULL COMMENT '日期',
    `ml`         INT      NOT NULL COMMENT '这一笔喝了多少（ml）',
    `drunk_at`   TIME     NULL COMMENT '时刻（ADR 0059）；NULL＝从旧的每日累计迁来的',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_wl_user_date` (`user_id`, `log_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '饮水明细：一笔一行（ADR 0059）';

INSERT INTO `water_log` (`user_id`, `log_date`, `ml`, `drunk_at`, `created_at`)
SELECT `user_id`, `log_date`, `water_ml`, NULL, `created_at`
FROM `diet_daily`
WHERE `water_ml` > 0;
