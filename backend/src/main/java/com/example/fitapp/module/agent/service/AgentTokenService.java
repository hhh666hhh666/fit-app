package com.example.fitapp.module.agent.service;

import com.example.fitapp.module.agent.entity.AgentToken;
import com.example.fitapp.module.agent.mapper.AgentTokenMapper;
import com.example.fitapp.module.agent.vo.AgentTokenCreatedVO;
import com.example.fitapp.module.agent.vo.AgentTokenStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * agent 令牌的生成、吊销与校验（ADR 0043）。
 *
 * <p>令牌是 32 字节随机数，暴力猜不到，所以这里用 SHA-256 而不是 BCrypt——校验每个请求都要做，
 * 也只有定值哈希才能按哈希直接查到是谁的。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentTokenService {

    private static final String PREFIX = "fit_";
    private static final int TOKEN_BYTES = 32;
    /** 最后使用时间的刷新间隔：只为给主人看「最近用过没」，不必每次请求都写库。 */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentTokenMapper agentTokenMapper;

    public AgentTokenStatusVO status(Long userId) {
        AgentToken t = agentTokenMapper.selectById(userId);
        return t == null
                ? new AgentTokenStatusVO(false, null, null)
                : new AgentTokenStatusVO(true, t.getCreatedAt(), t.getLastUsedAt());
    }

    /** 生成新令牌；已有的那把立即失效。 */
    @Transactional
    public AgentTokenCreatedVO regenerate(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        agentTokenMapper.deleteById(userId);
        AgentToken t = new AgentToken();
        t.setUserId(userId);
        t.setTokenHash(sha256(token));
        t.setCreatedAt(LocalDateTime.now());
        agentTokenMapper.insert(t);
        return new AgentTokenCreatedVO(token, t.getCreatedAt());
    }

    public void revoke(Long userId) {
        agentTokenMapper.deleteById(userId);
    }

    /** 校验令牌，返回所属用户；无效或已吊销返回 null。 */
    public Long authenticate(String token) {
        if (token == null || !token.startsWith(PREFIX)) {
            return null;
        }
        AgentToken t = agentTokenMapper.selectByHash(sha256(token));
        if (t == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (t.getLastUsedAt() == null || t.getLastUsedAt().plus(TOUCH_INTERVAL).isBefore(now)) {
            agentTokenMapper.touch(t.getUserId(), now);
        }
        return t.getUserId();
    }

    private static String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
