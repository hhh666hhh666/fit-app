package com.example.fitapp.module.stats.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.stats.service.StatsService;
import com.example.fitapp.module.stats.vo.StatsVO;
import com.example.fitapp.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计页接口：period=week|month|year（默认 week），一屏数据一次返回。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public Result<StatsVO> stats(@RequestParam(defaultValue = "week") String period) {
        return Result.success(statsService.stats(UserContext.requireUserId(), period));
    }
}
