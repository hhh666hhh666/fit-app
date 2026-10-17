package com.example.fitapp.module.profile.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.profile.dto.ProfileSaveDTO;
import com.example.fitapp.module.profile.service.ProfileService;
import com.example.fitapp.module.profile.vo.ProfileVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人档案接口：读一份档案（含今日消耗概览）／整体保存。
 * 「每日总消耗」的算式拆解一并返回，前端不再自己算（ADR 0029）。
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public Result<ProfileVO> get() {
        return Result.success(profileService.get(UserContext.requireUserId()));
    }

    @PutMapping
    public Result<ProfileVO> save(@Valid @RequestBody ProfileSaveDTO body) {
        return Result.success(profileService.save(UserContext.requireUserId(), body));
    }
}
