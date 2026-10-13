package com.example.fitapp.module.workout.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.workout.dto.WorkoutBatchDeleteDTO;
import com.example.fitapp.module.workout.dto.WorkoutCreateDTO;
import com.example.fitapp.module.workout.dto.WorkoutNoteDTO;
import com.example.fitapp.module.workout.dto.WorkoutUpdateDTO;
import com.example.fitapp.module.workout.service.WorkoutService;
import com.example.fitapp.module.workout.vo.LastRecordVO;
import com.example.fitapp.module.workout.vo.WorkoutDetailVO;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 训练记录接口：提交 / 列表 / 详情 / 修改 / 备注 / 删除（单条与批量）/ 某动作的上次数据。
 */
@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;

    @PostMapping
    public Result<WorkoutDetailVO> create(@Valid @RequestBody WorkoutCreateDTO body) {
        return Result.success(workoutService.create(UserContext.requireUserId(), body));
    }

    /** 批量删除：日志页长按多选删除。精确路径在 Spring MVC 里优先于 /{id} 模板。 */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody WorkoutBatchDeleteDTO body) {
        workoutService.batchDelete(UserContext.requireUserId(), body.getIds());
        return Result.success();
    }

    @GetMapping
    public Result<List<WorkoutListItemVO>> list(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(workoutService.list(UserContext.requireUserId(), Math.min(limit, 200)));
    }

    /** 精确路径在 Spring MVC 里优先于 /{id} 模板，不会冲突。 */
    @GetMapping("/last-record")
    public Result<LastRecordVO> lastRecord(@RequestParam Long exerciseId) {
        return Result.success(workoutService.lastRecord(UserContext.requireUserId(), exerciseId));
    }

    @GetMapping("/{id}")
    public Result<WorkoutDetailVO> detail(@PathVariable Long id) {
        return Result.success(workoutService.detail(UserContext.requireUserId(), id));
    }

    @PutMapping("/{id}/note")
    public Result<Void> updateNote(@PathVariable Long id, @Valid @RequestBody WorkoutNoteDTO body) {
        workoutService.updateNote(UserContext.requireUserId(), id, body.getNote());
        return Result.success();
    }

    /** 修改整条记录（详情页「编辑 → ✓」）：标题/备注/动作组次整体覆盖。 */
    @PutMapping("/{id}")
    public Result<WorkoutDetailVO> update(@PathVariable Long id, @Valid @RequestBody WorkoutUpdateDTO body) {
        return Result.success(workoutService.update(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workoutService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }
}
