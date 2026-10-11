package com.example.fitapp.module.auth.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.auth.dto.LoginDTO;
import com.example.fitapp.module.auth.dto.RegisterDTO;
import com.example.fitapp.module.auth.service.AuthService;
import com.example.fitapp.module.auth.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。这两条路径在 WebConfig 白名单里放行（无需携带 token）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO body) {
        return Result.success(authService.register(body));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO body) {
        return Result.success(authService.login(body));
    }
}
