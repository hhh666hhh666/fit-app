-- 标签识别用量流水（ADR 0018）：每次识别调用一条，只增不改不删；失败的也记（token 真实花了）。
--   cached_tokens 单列：输入 token 里命中隐式缓存的部分单价不同，混记算不清。
--   不记费用（拍定口径）；「我的」页外卡只露累计 token ＋ 请求次数，明细走分页接口。
CREATE TABLE `food_recognition` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT        NOT NULL COMMENT '所属用户',
    `model`         VARCHAR(64)   NOT NULL COMMENT '模型档（如 qwen3-vl-8b-instruct）',
    `tokens_in`     INT           NOT NULL DEFAULT 0 COMMENT '输入 token（含缓存命中部分）',
    `tokens_out`    INT           NOT NULL DEFAULT 0 COMMENT '输出 token',
    `cached_tokens` INT           NOT NULL DEFAULT 0 COMMENT '输入 token 中命中缓存的部分',
    `duration_ms`   INT           NOT NULL DEFAULT 0 COMMENT '调用耗时（毫秒）',
    `success`       TINYINT       NOT NULL DEFAULT 0 COMMENT '是否识别成功（0=失败，token 仍照记）',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_fr_user` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '标签识别用量流水（ADR 0018）';
