package com.example.fitapp.module.nutrition.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.nutrition.dto.FoodImportDTO;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.service.FoodService;
import com.example.fitapp.module.nutrition.vo.ImportResultVO;
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
import java.util.Map;

/**
 * 食物库接口：列表（搜索）/ 新建 / 编辑 / 删除 / JSON 文本导入 / 导出。
 */
@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @GetMapping
    public Result<List<Food>> list(@RequestParam(required = false) String q) {
        return Result.success(foodService.list(UserContext.requireUserId(), q));
    }

    @PostMapping
    public Result<Food> create(@Valid @RequestBody FoodSaveDTO body) {
        return Result.success(foodService.create(UserContext.requireUserId(), body));
    }

    @PutMapping("/{id}")
    public Result<Food> update(@PathVariable Long id, @Valid @RequestBody FoodSaveDTO body) {
        return Result.success(foodService.update(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        foodService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }

    @PostMapping("/import")
    public Result<ImportResultVO> importJson(@Valid @RequestBody FoodImportDTO body) {
        return Result.success(foodService.importText(UserContext.requireUserId(), body.getText()));
    }

    @GetMapping("/export")
    public Result<List<Map<String, Object>>> export() {
        return Result.success(foodService.exportAll(UserContext.requireUserId()));
    }
}
