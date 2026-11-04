package com.example.fitapp.module.admin.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.admin.service.AdminUserService;
import com.example.fitapp.module.admin.vo.AdminMeVO;
import com.example.fitapp.module.admin.vo.AdminUserVO;
import com.example.fitapp.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端 · 身份与用户列表（ADR 0048）。{@code /me} 给管理端登录后确认「是管理员」：
 * 不是的话拦截器已经回了「没有权限」，前端据此退出。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService service;

    @GetMapping("/me")
    public Result<AdminMeVO> me() {
        return Result.success(service.me(UserContext.requireUserId()));
    }

    @GetMapping("/users")
    public Result<List<AdminUserVO>> users() {
        return Result.success(service.list());
    }
}
