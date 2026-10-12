package com.example.fitapp.module.nutrition.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.nutrition.dto.SettingDTO;
import com.example.fitapp.module.nutrition.service.SettingService;
import com.example.fitapp.module.nutrition.vo.SettingVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 饮食设置与目标：读取（缺省给默认）/ 全量提交。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public Result<SettingVO> get() {
        return Result.success(settingService.get(UserContext.requireUserId()));
    }

    @PutMapping
    public Result<SettingVO> update(@Valid @RequestBody SettingDTO body) {
        return Result.success(settingService.update(UserContext.requireUserId(), body));
    }
}
