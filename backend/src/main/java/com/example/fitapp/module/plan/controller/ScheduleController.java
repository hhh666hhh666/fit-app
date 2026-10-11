package com.example.fitapp.module.plan.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.plan.dto.ScheduleSetDTO;
import com.example.fitapp.module.plan.service.ScheduleService;
import com.example.fitapp.module.plan.vo.ScheduleDayVO;
import com.example.fitapp.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 周训练安排接口：读七天 / 设置某天。
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public Result<List<ScheduleDayVO>> week() {
        return Result.success(scheduleService.week(UserContext.requireUserId()));
    }

    @PutMapping("/{day}")
    public Result<Void> set(@PathVariable int day, @RequestBody ScheduleSetDTO body) {
        scheduleService.set(UserContext.requireUserId(), day, body);
        return Result.success();
    }
}
