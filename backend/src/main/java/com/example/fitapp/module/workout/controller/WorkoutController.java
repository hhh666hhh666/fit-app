package com.example.fitapp.module.workout.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.workout.dto.WorkoutBatchDeleteDTO;
import com.example.fitapp.module.workout.dto.WorkoutCreateDTO;
import com.example.fitapp.module.workout.dto.WorkoutNoteDTO;
import com.example.fitapp.module.workout.dto.WorkoutUpdateDTO;
import com.example.fitapp.module.workout.service.WorkoutService;
import com.example.fitapp.module.workout.vo.LastRecordVO;
import com.example.fitapp.module.workout.vo.MonthSummaryVO;
import com.example.fitapp.module.workout.vo.WorkoutDetailVO;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
import com.example.fitapp.module.workout.vo.WorkoutTotalsVO;
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

    /**
     * 日志列表：`month=YYYY-MM` 按月过滤（不传＝不过滤，首页「上次训练」用它取最近一条）；
     * `offset` 供触底分页。月份格式不对返回 400。
     */
    @GetMapping
    public Result<List<WorkoutListItemVO>> list(@RequestParam(defaultValue = "50") int limit,
                                                @RequestParam(defaultValue = "0") int offset,
                                                @RequestParam(required = false) String month) {
        return Result.success(workoutService.list(UserContext.requireUserId(),
                Math.min(limit, 200), Math.max(offset, 0), month));
    }

    /** 日志页按月概览：当月次数与总容量 ＋ 上月同口径（环比基准）。不传 month＝当月。 */
    @GetMapping("/month-summary")
    public Result<MonthSummaryVO> monthSummary(@RequestParam(required = false) String month) {
        return Result.success(workoutService.monthSummary(UserContext.requireUserId(), month));
    }

    /** 精确路径在 Spring MVC 里优先于 /{id} 模板，不会冲突。 */
    @GetMapping("/last-record")
    public Result<LastRecordVO> lastRecord(@RequestParam Long exerciseId) {
        return Result.success(workoutService.lastRecord(UserContext.requireUserId(), exerciseId));
    }

    /**
     * 「我的」页累计口径：全量训练次数与总容量。
     * 刻意不做在列表接口上——列表有 limit 封顶，拿它求和会把「累计」算成「最近 N 次」（ADR 0023）。
     */
    @GetMapping("/totals")
    public Result<WorkoutTotalsVO> totals() {
        return Result.success(workoutService.totals(UserContext.requireUserId()));
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
