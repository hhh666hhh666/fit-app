-- 容量口径与 PR 口径改造（ADR 0023／0024）：历史数据一次性重算。
--
-- ① 总容量：只累计**力量动作**（有氧 cardio 不计入；未挂动作库的组判不出肌群，照旧计入）。
-- ② PR 标记：改为「**重量优先、同重量比次数**」，并**按时间序回放**重打——保住「刷新时刻」语义
--    （旧口径是提交时按 `weight×reps` 严格大于刷新；直接标「当前最优那一组」会把历史上的破纪录高亮全抹掉）。
--
-- ⚠️ 回放的写法有两个 MySQL 限制要绕：
--    ① 不允许在子查询里引用正在 UPDATE 的那张表（错误 1093）→ 先把候选组次落进**临时表**；
--    ② 不允许同一条语句里引用同一张**临时**表两次（错误 1137）→ 「此前最佳」用**窗口函数**算好，
--       整条 UPDATE 只引用临时表一次。

UPDATE `workout`
SET `total_volume` = 0;

UPDATE `workout` w
    JOIN (SELECT wb.workout_id AS wid, SUM(ws.weight * ws.reps) AS vol
          FROM workout_set ws
                   JOIN workout_block wb ON wb.id = ws.block_id
                   LEFT JOIN exercise e ON e.id = wb.exercise_id
          WHERE e.muscle IS NULL
             OR e.muscle <> 'cardio'
          GROUP BY wb.workout_id) t ON t.wid = w.id
SET w.total_volume = t.vol;

-- 力量动作组次快照：顺序键＝训练时间 → 动作在当次内的顺序 → 组序号（与原逐组递进的提交顺序一致）。
DROP TEMPORARY TABLE IF EXISTS tmp_pr_replay;

CREATE TEMPORARY TABLE tmp_pr_replay AS
SELECT ws.id          AS set_id,
       wb.exercise_id AS exercise_id,
       w.created_at   AS workout_at,
       wb.sort_order  AS block_order,
       ws.set_index   AS set_index,
       ws.weight      AS weight,
       ws.reps        AS reps
FROM workout_set ws
         JOIN workout_block wb ON wb.id = ws.block_id
         JOIN workout w ON w.id = wb.workout_id
         JOIN exercise e ON e.id = wb.exercise_id
WHERE e.muscle <> 'cardio';

ALTER TABLE tmp_pr_replay
    ADD PRIMARY KEY (set_id),
    ADD KEY idx_replay (exercise_id, workout_at, block_order, set_index);

UPDATE `workout_set`
SET `pr` = 0;

-- 回放：把 (重量, 次数) 编码成**等宽字符串**（先重量后次数，字典序＝先比重量再比次数），
-- 再用窗口函数取「此前所有组里的最大键值」——它为空或本组键值更大，就是一次刷新。
UPDATE workout_set ws
    JOIN (SELECT set_id,
                 cur_key,
                 MAX(cur_key) OVER (PARTITION BY exercise_id
                     ORDER BY workout_at, block_order, set_index
                     ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING) AS prior_key
          FROM (SELECT set_id, exercise_id, workout_at, block_order, set_index,
                       CONCAT(LPAD(CAST(weight * 100 AS UNSIGNED), 10, '0'),
                              LPAD(CAST(reps AS CHAR), 10, '0')) AS cur_key
                FROM tmp_pr_replay) k) t ON t.set_id = ws.id
SET ws.pr = IF(t.prior_key IS NULL OR t.cur_key > t.prior_key, 1, 0);

DROP TEMPORARY TABLE IF EXISTS tmp_pr_replay;
