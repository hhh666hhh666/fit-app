package com.example.fitapp.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.auth.dto.LoginDTO;
import com.example.fitapp.module.auth.dto.RegisterDTO;
import com.example.fitapp.module.auth.vo.LoginVO;
import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import com.example.fitapp.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证：注册与登录（最小集，不含改密/找回/风控，见 docs/adr/0003-register-login-auth.md）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /** 注册：用户名唯一；注册成功即登录（直接发 token）。 */
    public LoginVO register(RegisterDTO dto) {
        String username = dto.getUsername().trim();
        Long count = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(ENCODER.encode(dto.getPassword()));
        userMapper.insert(user);
        return toLoginVO(user);
    }

    /** 登录：用户名＋密码校验，通过后签发 JWT。 */
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername().trim()));
        // 用户不存在与密码错误返回同一句提示，不泄露用户名是否存在
        if (user == null || !ENCODER.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户名或密码不正确");
        }
        return toLoginVO(user);
    }

    private LoginVO toLoginVO(User user) {
        return new LoginVO(jwtUtil.generateToken(user.getId()), user.getId(), user.getUsername());
    }
}
