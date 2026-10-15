package com.example.fitapp.module.exercise.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.exercise.dto.ExerciseUpsertDTO;
import com.example.fitapp.module.exercise.dto.ExerciseBatchDeleteDTO;
import com.example.fitapp.module.exercise.service.ExerciseService;
import com.example.fitapp.module.exercise.vo.ExerciseVO;
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
 * 动作库接口：预设 + 自定义；「删除」为下架语义（archived=1）。
 */
@RestController
@RequestMapping("/api/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    @GetMapping
    public Result<List<ExerciseVO>> list(@RequestParam(required = false) String muscle,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(exerciseService.list(UserContext.requireUserId(), muscle, keyword));
    }

    @PostMapping
    public Result<ExerciseVO> create(@Valid @RequestBody ExerciseUpsertDTO body) {
        return Result.success(exerciseService.create(UserContext.requireUserId(), body));
    }

    @PutMapping("/{id}")
    public Result<ExerciseVO> update(@PathVariable Long id, @Valid @RequestBody ExerciseUpsertDTO body) {
        return Result.success(exerciseService.update(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable Long id) {
        exerciseService.archive(UserContext.requireUserId(), id);
        return Result.success();
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody ExerciseBatchDeleteDTO body) {
        exerciseService.batchArchive(UserContext.requireUserId(), body.getIds());
        return Result.success();
    }
}
