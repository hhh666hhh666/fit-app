package com.example.fitapp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * agent 令牌（每用户一把）。表结构以迁移脚本 {@code V31__agent_token.sql} 为准，口径见 ADR 0043。
 */
@Data
@TableName("agent_token")
public class AgentToken {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 令牌的 SHA-256；明文不落库。 */
    private String tokenHash;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;
}
