package com.example.fitapp.security;

import com.example.fitapp.common.Result;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 管理端接口（{@code /api/admin/**}）的唯一把关处（ADR 0048）：排在 {@link JwtInterceptor} 之后，
 * 当前用户必须带管理员标记。每次请求都查库——标记改了立即生效，不用等 JWT 过期。
 * 和 App 其他接口一样回 HTTP 200 ＋ 业务码。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Long userId = UserContext.getUserId();
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user != null && Boolean.TRUE.equals(user.getAdmin())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                userId == null ? Result.fail(ResultCode.UNAUTHORIZED) : Result.fail(ResultCode.FORBIDDEN, "没有权限"));
        return false;
    }
}
