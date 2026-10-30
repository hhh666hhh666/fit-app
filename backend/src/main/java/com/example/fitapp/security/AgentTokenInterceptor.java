package com.example.fitapp.security;

import com.example.fitapp.common.Result;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.agent.service.AgentTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 外部 agent 取数接口的鉴权（ADR 0043）：只认 agent 令牌，不认登录态 JWT。
 *
 * <p>失败返回**真实的 401**：与 App 走的 {@link JwtInterceptor}（HTTP 200 ＋ 业务码）不同，
 * 外部调用方的 HTTP 客户端多半把 200 当成功，再把错误信封当数据解析。</p>
 */
@Component
@RequiredArgsConstructor
public class AgentTokenInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";

    private final AgentTokenService agentTokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(AUTHORIZATION);
        Long userId = header != null && header.startsWith(BEARER)
                ? agentTokenService.authenticate(header.substring(BEARER.length()))
                : null;
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("WWW-Authenticate", "Bearer");
            objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.UNAUTHORIZED, "agent 令牌无效或已吊销"));
            return false;
        }
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
