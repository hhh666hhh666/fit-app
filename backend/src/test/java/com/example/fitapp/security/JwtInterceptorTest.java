package com.example.fitapp.security;

import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 令牌版本（ADR 0050）：改密码后旧令牌失效；账号注销后令牌失效；老令牌不带版本按 0。 */
class JwtInterceptorTest {

    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                User.class);
    }

    private final JwtUtil jwtUtil = new JwtUtil("test-secret-for-unit-tests-0123456789abcdef0123456789abcdef", 7);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final JwtInterceptor interceptor = new JwtInterceptor(jwtUtil, userMapper, new ObjectMapper());

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private boolean pass(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
        req.addHeader("Authorization", "Bearer " + token);
        return interceptor.preHandle(req, new MockHttpServletResponse(), null);
    }

    private static User userWithVersion(int v) {
        User u = new User();
        u.setId(7L);
        u.setTokenVersion(v);
        return u;
    }

    @Test
    void 版本一致放行() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(userWithVersion(3));
        assertTrue(pass(jwtUtil.generateToken(7L, 3)));
        assertEquals(7L, UserContext.getUserId());
    }

    @Test
    void 改过密码的旧令牌被拒() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(userWithVersion(4));
        assertFalse(pass(jwtUtil.generateToken(7L, 3)));
    }

    @Test
    void 账号没了令牌失效() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertFalse(pass(jwtUtil.generateToken(7L, 0)));
    }

    @Test
    void 没签版本的老令牌按零处理() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(userWithVersion(0));
        assertTrue(pass(jwtUtil.generateToken(7L, null)));
    }
}
