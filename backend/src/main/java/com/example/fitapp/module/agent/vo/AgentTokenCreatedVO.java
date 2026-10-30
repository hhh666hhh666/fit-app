package com.example.fitapp.module.agent.vo;

import java.time.LocalDateTime;

/**
 * 刚生成的令牌：{@code token} 是明文，只有这一次机会拿到。
 */
public record AgentTokenCreatedVO(String token, LocalDateTime createdAt) {
}
