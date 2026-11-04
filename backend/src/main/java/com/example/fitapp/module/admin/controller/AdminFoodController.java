package com.example.fitapp.module.admin.controller;

import com.example.fitapp.common.PageResult;
import com.example.fitapp.common.Result;
import com.example.fitapp.module.admin.dto.AdminIdsDTO;
import com.example.fitapp.module.admin.dto.AdminImportDTO;
import com.example.fitapp.module.admin.dto.PresetFoodSaveDTO;
import com.example.fitapp.module.admin.service.AdminFoodService;
import com.example.fitapp.module.admin.vo.AdminFoodRowVO;
import com.example.fitapp.module.admin.vo.ImportPreviewVO;
import com.example.fitapp.module.nutrition.entity.Food;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端 · 预置食物（ADR 0048）。管理员校验在 {@code AdminInterceptor}。
 * 没有删除接口：预置的删除就是下架。
 */
@RestController
@RequestMapping("/api/admin/foods")
@RequiredArgsConstructor
public class AdminFoodController {

    private final AdminFoodService service;

    @GetMapping
    public Result<PageResult<AdminFoodRowVO>> page(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String category,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String sort,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "50") long size) {
        return Result.success(service.page(q, category, status, sort, page, size));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(service.categories());
    }

    @GetMapping("/name-taken")
    public Result<Boolean> nameTaken(@RequestParam String name, @RequestParam(required = false) Long excludeId) {
        return Result.success(service.nameTaken(name, excludeId));
    }

    @GetMapping("/export")
    public Result<List<Map<String, Object>>> export() {
        return Result.success(service.exportAll());
    }

    @GetMapping("/{id}")
    public Result<Food> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @PostMapping
    public Result<Food> create(@Valid @RequestBody PresetFoodSaveDTO body) {
        return Result.success(service.create(body));
    }

    @PutMapping("/{id}")
    public Result<Food> update(@PathVariable Long id, @Valid @RequestBody PresetFoodSaveDTO body) {
        return Result.success(service.update(id, body));
    }

    @PostMapping("/archive")
    public Result<Void> archive(@Valid @RequestBody AdminIdsDTO body) {
        service.archive(body.getIds());
        return Result.success();
    }

    @PostMapping("/unarchive")
    public Result<Void> unarchive(@Valid @RequestBody AdminIdsDTO body) {
        service.unarchive(body.getIds());
        return Result.success();
    }

    @PostMapping("/import/preview")
    public Result<ImportPreviewVO> previewImport(@Valid @RequestBody AdminImportDTO body) {
        return Result.success(service.previewImport(body.getText()));
    }

    @PostMapping("/import")
    public Result<ImportPreviewVO> importJson(@Valid @RequestBody AdminImportDTO body) {
        return Result.success(service.importText(body.getText()));
    }
}
