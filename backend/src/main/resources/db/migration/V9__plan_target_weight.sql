-- 计划动作编排加「目标重量」：会话页「开始训练」按它预填每组负荷默认值（2026-10-12 口径）。
-- 与 target_sets / target_reps 同层：NULL=未设；只作默认值，训练中的临场改动不回写计划（scope #9）。
ALTER TABLE `plan_exercise`
    ADD COLUMN `target_weight` DECIMAL(5, 2) NULL COMMENT '目标重量 kg（NULL=未设）' AFTER `target_reps`;
