package com.example.fitapp.module.nutrition.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.nutrition.dto.ComboSaveDTO;
import com.example.fitapp.module.nutrition.service.ComboService;
import com.example.fitapp.module.nutrition.vo.ComboVO;
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
 * 食物组合接口：列表 / 单条 / 新建 / 编辑（明细整体覆盖）/ 删除（连带明细）。
 * 「整份加入一餐」不走这里——由 {@code POST /api/diet/meals/batch} 展开为多条普通条目（ADR 0012）。
 */
@RestController
@RequestMapping("/api/combos")
@RequiredArgsConstructor
public class ComboController {

    private final ComboService comboService;

    @GetMapping
    public Result<List<ComboVO>> list() {
        return Result.success(comboService.list(UserContext.requireUserId()));
    }

    @GetMapping("/{id}")
    public Result<ComboVO> get(@PathVariable Long id) {
        return Result.success(comboService.get(UserContext.requireUserId(), id));
    }

    @PostMapping
    public Result<ComboVO> create(@Valid @RequestBody ComboSaveDTO body) {
        return Result.success(comboService.create(UserContext.requireUserId(), body));
    }

    @PutMapping("/{id}")
    public Result<ComboVO> update(@PathVariable Long id, @Valid @RequestBody ComboSaveDTO body) {
        return Result.success(comboService.update(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        comboService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }
}
