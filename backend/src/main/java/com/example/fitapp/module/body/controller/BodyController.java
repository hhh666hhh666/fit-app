package com.example.fitapp.module.body.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.body.dto.BodyBatchDeleteDTO;
import com.example.fitapp.module.body.dto.BodySaveDTO;
import com.example.fitapp.module.body.service.BodyService;
import com.example.fitapp.module.body.vo.BodyOverviewVO;
import com.example.fitapp.module.body.vo.BodyRecordVO;
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
 * 身体档案接口：一屏总览／记录明细／录入／修改／删除（单条与批量）。
 * 记录＝一次测定，体重与四围同一行（ADR 0017）。
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

    /** 记录明细（最新在前）；offset 用于前端触底分页。 */
    @GetMapping("/records")
    public Result<List<BodyRecordVO>> records(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return Result.success(bodyService.records(
                UserContext.requireUserId(), Math.min(limit, 100), Math.max(offset, 0)));
    }

    @PostMapping
    public Result<BodyRecordVO> create(@Valid @RequestBody BodySaveDTO body) {
        return Result.success(bodyService.create(UserContext.requireUserId(), body));
    }

    /** 批量删除：身体档案页长按多选删除。精确路径在 Spring MVC 里优先于 /{id} 模板。 */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody BodyBatchDeleteDTO body) {
        bodyService.batchDelete(UserContext.requireUserId(), body.getIds());
        return Result.success();
    }

    /** 修改一次测定（身体档案页点行进编辑弹层）：时刻与五项数值整体覆盖。 */
    @PutMapping("/{id}")
    public Result<BodyRecordVO> update(@PathVariable Long id, @Valid @RequestBody BodySaveDTO body) {
        return Result.success(bodyService.update(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bodyService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }
}
