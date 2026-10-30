package com.example.fitapp.module.agent.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.agent.service.AgentTokenService;
import com.example.fitapp.module.agent.vo.AgentTokenCreatedVO;
import com.example.fitapp.module.agent.vo.AgentTokenStatusVO;
import com.example.fitapp.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent 令牌管理：App 用，走登录态 JWT；令牌自己不能生成或吊销令牌（ADR 0043）。
 */
@RestController
@RequestMapping("/api/agent/token")
@RequiredArgsConstructor
public class AgentTokenController {

    private final AgentTokenService agentTokenService;

    @GetMapping
    public Result<AgentTokenStatusVO> status() {
        return Result.success(agentTokenService.status(UserContext.requireUserId()));
    }

    /** 生成新令牌（旧的立即失效）；明文只在这个响应里出现一次。 */
    @PostMapping
    public Result<AgentTokenCreatedVO> regenerate() {
        return Result.success(agentTokenService.regenerate(UserContext.requireUserId()));
    }

    @DeleteMapping
    public Result<Void> revoke() {
        agentTokenService.revoke(UserContext.requireUserId());
        return Result.success();
    }
}
