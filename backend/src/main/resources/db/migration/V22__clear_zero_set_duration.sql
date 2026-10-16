-- 修正历史脏数据：`duration_sec = 0` 全部改回 NULL。
--
-- 起因：会话页 completeSet() 曾先写 doneAt、再调 setDurationSec()，而后者用 lastDoneAt()
-- 找「上一组完成时刻」，于是每次都拿刚勾的这一组自己当起点，净用时恒为 0（bb94e42 引入，
-- v0.8.0～v0.8.2 期间提交的场次全部受影响）。0 是错误写入，不是真实值——V21 定的口径是
-- 「无用时存 NULL」，这里把污染值归位。
--
-- 真实用时无法反推：workout_set 只存 duration_sec 与 created_at，没存每组的完成时刻。
-- 前端修复见同一提交（先算后用、并排除本组）。

UPDATE `workout_set`
SET `duration_sec` = NULL
WHERE `duration_sec` = 0;
