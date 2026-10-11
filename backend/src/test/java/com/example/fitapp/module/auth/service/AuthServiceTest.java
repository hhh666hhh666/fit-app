package com.example.fitapp.module.auth.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.auth.dto.LoginDTO;
import com.example.fitapp.module.auth.dto.RegisterDTO;
import com.example.fitapp.module.auth.vo.LoginVO;
import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import com.example.fitapp.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 查重漏了会一直注册重复用户名；密码比对写反会「错误密码也能登录」。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Mock
    private UserMapper userMapper;

    private final JwtUtil jwtUtil =
            new JwtUtil("test-secret-for-unit-tests-0123456789abcdef0123456789abcdef", 7);

    private AuthService service() {
        return new AuthService(userMapper, jwtUtil);
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("lzh");
        dto.setPassword("secret123");

        BusinessException ex = assertThrows(BusinessException.class, () -> service().register(dto));
        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
    }

    @Test
    void register_success_returnsTokenAndTrimsUsername() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("  lzh  ");
        dto.setPassword("secret123");

        LoginVO vo = service().register(dto);
        assertEquals(1L, vo.userId());
        assertEquals("lzh", vo.username());
        assertNotNull(vo.token());
    }

    @Test
    void login_wrongPassword_throws() {
        User stored = new User();
        stored.setId(1L);
        stored.setUsername("lzh");
        stored.setPasswordHash(ENCODER.encode("right-password"));
        when(userMapper.selectOne(any())).thenReturn(stored);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("lzh");
        dto.setPassword("wrong-password");

        assertThrows(BusinessException.class, () -> service().login(dto));
    }

    @Test
    void login_unknownUser_throws() {
        when(userMapper.selectOne(any())).thenReturn(null);
        LoginDTO dto = new LoginDTO();
        dto.setUsername("nobody");
        dto.setPassword("secret123");

        assertThrows(BusinessException.class, () -> service().login(dto));
    }

    @Test
    void login_success_returnsToken() {
        User stored = new User();
        stored.setId(1L);
        stored.setUsername("lzh");
        stored.setPasswordHash(ENCODER.encode("right-password"));
        when(userMapper.selectOne(any())).thenReturn(stored);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("lzh");
        dto.setPassword("right-password");

        LoginVO vo = service().login(dto);
        assertEquals("lzh", vo.username());
        assertNotNull(vo.token());
    }
}
