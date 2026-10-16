-- 餐照从「库内 BLOB」迁到「文件系统」（ADR 0026）。
--
-- 为什么迁：图片是这套数据里唯一会持续长大的东西，而 `mysqldump` 不支持排除列——
--   它把「每日全量备份」从 KB 级推到 GB 级；到图片接近 20GB 上限时，单次导出要先在
--   磁盘上生成约 40GB 的中间文本（服务器可用磁盘只有 27G），备份会从「慢」变成「做不到」。
--   图片出库后，库回到 1MB 级、每日全量重新变得永远跑得动，图片另走增量快照。
--
-- 本迁移刻意**只做「加列 + 放开 NOT NULL」，不动 image/thumb 的数据、也不删列**：
--   落盘由应用启动时执行（MealPhotoFileMigrator，幂等）——它把 BLOB 写成文件、回填
--   path/thumb_bytes，**成功之后**才把两列置 NULL。因此中途无论哪一步失败，
--   原始数据都完整留在库里，不存在「删列后才发现搬丢了」这种不可逆的中间态。
--
-- 为什么保留 image/thumb 两列而不顺手删掉：
--   ① 删列不可逆，而落盘必须在应用运行时做（迁移阶段拿不到文件系统与挂载卷）；
--   ② 列留着但恒为 NULL 不占空间——库体积的收益已经拿到；
--   ③ 等确认线上无残留后，再单独一个迁移清理，风险最小（见 ADR 0026 的收尾待办）。
ALTER TABLE `meal_photo`
    ADD COLUMN `path` VARCHAR(255) NULL
        COMMENT '图片相对路径（不含扩展名与根目录）：{userId}/{logDate}/{meal}；见 ADR 0026' AFTER `size_bytes`,
    ADD COLUMN `thumb_bytes` INT NULL
        COMMENT '缩略图字节数（缩略图文件在文件系统；老数据无缩略图时为 NULL）' AFTER `path`,
    MODIFY COLUMN `image` MEDIUMBLOB NULL
        COMMENT '⚠️ 已迁至文件系统（见 path）；新数据不再写入，搬运完成后为 NULL',
    MODIFY COLUMN `thumb` MEDIUMBLOB NULL
        COMMENT '⚠️ 同上；为 NULL 时读取端回退原图';
