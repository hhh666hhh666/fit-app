package com.example.fitapp.module.plan.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.plan.dto.PlanSaveDTO;
import com.example.fitapp.module.plan.service.PlanService;
import com.example.fitapp.module.plan.vo.PlanDetailVO;
import com.example.fitapp.module.plan.vo.PlanListItemVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 训练计划接口：列表 / 详情 / 新建 / 更新 / 删除。
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public Result<List<PlanListItemVO>> list() {
        return Result.success(planService.list(UserContext.requireUserId()));
    }

    @GetMapping("/{id}")
    public Result<PlanDetailVO> detail(@PathVariable Long id) {
        return Result.success(planService.detail(UserContext.requireUserId(), id));
    }

    @PostMapping
    public Result<PlanDetailVO> create(@Valid @RequestBody PlanSaveDTO body) {
        return Result.success(planService.create(UserContext.requireUserId(), body));
    }

    @PutMapping("/{id}")
    public Result<PlanDetailVO> update(@PathVariable Long id, @Valid @RequestBody PlanSaveDTO body) {
        return Result.success(planService.update(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }
}
