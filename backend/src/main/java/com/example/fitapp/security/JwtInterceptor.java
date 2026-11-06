package com.example.fitapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.fitapp.common.Result;
import com.example.fitapp.common.ResultCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER)) {
            writeUnauthorized(response);
            return false;
        }
        JwtUtil.TokenClaims claims = jwtUtil.parseToken(header.substring(BEARER.length()));
        if (claims == null || !isCurrent(claims)) {
            writeUnauthorized(response);
            return false;
        }
        UserContext.setUserId(claims.userId());
        return true;
    }

    /**
     * 令牌还作数吗（ADR 0050）：账号还在，且签发时的版本等于当前版本。
     * 改密码会把版本加一，于是其他设备的旧令牌在这里被拒；注销后读不到这一行，同样被拒。
     * 每个请求多一次主键查询——用户只有个位数，代价可以忽略。
     */
    private boolean isCurrent(JwtUtil.TokenClaims claims) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .select(User::getId, User::getTokenVersion)
                .eq(User::getId, claims.userId()));
        if (user == null) {
            return false;
        }
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return current == claims.tokenVersion();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.UNAUTHORIZED));
    }
}
