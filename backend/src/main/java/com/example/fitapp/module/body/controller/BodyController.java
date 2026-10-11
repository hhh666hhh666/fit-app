package com.example.fitapp.module.body.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.body.dto.BodySaveDTO;
import com.example.fitapp.module.body.service.BodyService;
import com.example.fitapp.module.body.vo.BodyOverviewVO;
import com.example.fitapp.module.body.vo.BodyRecordVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 身体档案接口：一屏总览／体重明细／录入。
 */
@RestController
@RequestMapping("/api/body")
@RequiredArgsConstructor
public class BodyController {

    private final BodyService bodyService;

    @GetMapping
    public Result<BodyOverviewVO> overview() {
        return Result.success(bodyService.overview(UserContext.requireUserId()));
    }

    @GetMapping("/records")
    public Result<List<BodyRecordVO>> records(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(bodyService.records(UserContext.requireUserId(), Math.min(limit, 100)));
    }

    @PostMapping
    public Result<BodyRecordVO> create(@Valid @RequestBody BodySaveDTO body) {
        return Result.success(bodyService.create(UserContext.requireUserId(), body));
    }
}
