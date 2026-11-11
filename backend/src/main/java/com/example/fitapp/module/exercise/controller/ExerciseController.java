package com.example.fitapp.module.exercise.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.exercise.dto.ExerciseUpsertDTO;
import com.example.fitapp.module.exercise.dto.ExerciseBatchDeleteDTO;
import com.example.fitapp.module.exercise.service.ExerciseService;
import com.example.fitapp.module.exercise.storage.ExerciseMediaStorage;
import com.example.fitapp.module.exercise.vo.ExerciseDetailVO;
import com.example.fitapp.module.exercise.vo.ExerciseVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    /** 动作库里看得到的动作数（「我的」页用，不拉列表）。 */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(exerciseService.count(UserContext.requireUserId()));
    }

    /** 动作详情：基本信息 ＋ 动作资料（ADR 0055）。 */
    @GetMapping("/{id}")
    public Result<ExerciseDetailVO> detail(@PathVariable Long id) {
        return Result.success(exerciseService.detail(UserContext.requireUserId(), id));
    }

    /**
     * 动图（kind=gif，默认）或缩略图（kind=thumb）的二进制，不走 Result 信封；前端带 token fetch → blob。
     * 文件不会变，允许浏览器私有缓存。
     */
    @GetMapping("/{id}/media")
    public ResponseEntity<Resource> media(@PathVariable Long id,
                                          @RequestParam(defaultValue = "gif") String kind) {
        ExerciseMediaStorage.Kind k = "thumb".equals(kind) ? ExerciseMediaStorage.Kind.THUMB : ExerciseMediaStorage.Kind.GIF;
        Path file = exerciseService.media(UserContext.requireUserId(), id, k);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(k.mime))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate())
                .body(new FileSystemResource(file));
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
