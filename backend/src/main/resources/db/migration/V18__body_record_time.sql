-- 身体档案的测定时刻精确到分（ADR 0017）。
--   DATE → DATETIME：同一天记多条（晨起空腹／练后称重）时，列表要能靠时分区分。
--   存量回填：把 created_at 的时分秒搬到 measured_at——created_at 是 MySQL 记的真实录入时刻，
--   「当天记当天」的数据它就是最准的估计；用 00:00 会让历史全部显示成「某日 00:00」。
--   注意：跨天补记（10-12 录 10-11 的体重）搬过去的是 10-12 的时分，只为显示区分，不影响排序口径。
ALTER TABLE `body_record`
    MODIFY COLUMN `measured_at` DATETIME NOT NULL COMMENT '测定时刻（精确到分；同一天可多条）';

UPDATE `body_record`
SET `measured_at` = ADDTIME(`measured_at`, TIME(`created_at`))
WHERE TIME(`measured_at`) = '00:00:00';
