package com.example.fitapp.module.admin.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.admin.dto.AdminIdsDTO;
import com.example.fitapp.module.admin.dto.AdminImportDTO;
import com.example.fitapp.module.admin.dto.PresetExerciseSaveDTO;
import com.example.fitapp.module.admin.service.AdminExerciseImport;
import com.example.fitapp.module.admin.service.AdminExerciseService;
import com.example.fitapp.module.admin.vo.AdminExerciseVO;
import com.example.fitapp.module.admin.vo.ImportPreviewVO;
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

/** 管理端 · 预置动作（ADR 0048）。没有删除接口：预置的删除就是下架。导入导出见 ADR 0055。 */
@RestController
@RequestMapping("/api/admin/exercises")
@RequiredArgsConstructor
public class AdminExerciseController {

    private final AdminExerciseService service;
    private final AdminExerciseImport importer;

    @GetMapping
    public Result<List<AdminExerciseVO>> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String muscle,
                                              @RequestParam(required = false) String status) {
        return Result.success(service.list(q, muscle, status));
    }

    @PostMapping
    public Result<AdminExerciseVO> create(@Valid @RequestBody PresetExerciseSaveDTO body) {
        return Result.success(service.create(body));
    }

    @PutMapping("/{id}")
    public Result<AdminExerciseVO> update(@PathVariable Long id, @Valid @RequestBody PresetExerciseSaveDTO body) {
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

    /** 全部预置（含已下架），格式同导入。 */
    @GetMapping("/export")
    public Result<List<Map<String, Object>>> export() {
        return Result.success(importer.export());
    }

    @PostMapping("/import/preview")
    public Result<ImportPreviewVO> previewImport(@Valid @RequestBody AdminImportDTO body) {
        return Result.success(importer.preview(body.getText()));
    }

    @PostMapping("/import")
    public Result<ImportPreviewVO> importJson(@Valid @RequestBody AdminImportDTO body) {
        return Result.success(importer.importText(body.getText()));
    }
}
