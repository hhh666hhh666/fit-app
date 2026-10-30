-- 外部 agent 的只读专用令牌（ADR 0043）：每用户一把，只存哈希；重新生成＝换一行，吊销＝删行。
CREATE TABLE `agent_token` (
    `user_id`      BIGINT   NOT NULL COMMENT '所属用户',
    `token_hash`   CHAR(64) NOT NULL COMMENT '令牌的 SHA-256（明文只在生成时返回一次）',
    `last_used_at` DATETIME NULL COMMENT '最后使用时间（未用过为空）',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_agent_token_hash` (`token_hash`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '外部 agent 令牌';
