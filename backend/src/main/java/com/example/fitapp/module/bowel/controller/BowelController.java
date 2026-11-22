package com.example.fitapp.module.bowel.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.bowel.dto.BowelSaveDTO;
import com.example.fitapp.module.bowel.entity.BowelRecord;
import com.example.fitapp.module.bowel.service.BowelService;
import com.example.fitapp.module.bowel.vo.BowelStatsVO;
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

/** 排便记录（ADR 0061）：记一次／改／删（进回收站）／历史／统计。 */
@RestController
@RequestMapping("/api/bowel")
@RequiredArgsConstructor
public class BowelController {

    private final BowelService service;

    /** 历史：最近的在前；offset 给触底分页。 */
    @GetMapping
    public Result<List<BowelRecord>> list(@RequestParam(defaultValue = "30") int limit,
                                          @RequestParam(defaultValue = "0") int offset) {
        return Result.success(service.list(UserContext.requireUserId(), limit, offset));
    }

    @PostMapping
    public Result<BowelRecord> create(@Valid @RequestBody BowelSaveDTO body) {
        return Result.success(service.create(UserContext.requireUserId(), body));
    }

    @PutMapping("/{id}")
    public Result<BowelRecord> update(@PathVariable Long id, @Valid @RequestBody BowelSaveDTO body) {
        return Result.success(service.update(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(UserContext.requireUserId(), id);
        return Result.success();
    }

    /** 统计：days＝最近几天（含今天）；0＝从第一条记录起。 */
    @GetMapping("/stats")
    public Result<BowelStatsVO> stats(@RequestParam(defaultValue = "30") int days) {
        return Result.success(service.stats(UserContext.requireUserId(), days));
    }
}
