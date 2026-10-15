-- 组次净用时：这一组从「上一组完成」到「本组完成」的净时间（秒），剔除暂停段（ADR 0022）；
-- 第一组从开始训练算起。与 workout.duration_sec 同口径（都是净时间），所以「各组用时之和」与训练时长可比。
-- NULL ＝ 无用时：全部历史数据如此；详情页编辑是整体覆盖，没填用时的行保存后也是 NULL。

ALTER TABLE `workout_set`
    ADD COLUMN `duration_sec` INT NULL COMMENT '该组净用时（秒，剔除暂停）；无用时为 NULL' AFTER `pr`;
