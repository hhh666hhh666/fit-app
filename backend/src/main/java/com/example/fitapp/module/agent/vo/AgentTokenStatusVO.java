package com.example.fitapp.module.agent.vo;

import java.time.LocalDateTime;

/**
 * 令牌状态（「我的」页小卡用）：不含令牌本身——明文只在生成时返回一次。
 */
public record AgentTokenStatusVO(boolean exists, LocalDateTime createdAt, LocalDateTime lastUsedAt) {
}
