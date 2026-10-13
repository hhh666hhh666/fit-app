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
 * 食物库接口：列表（搜索；lite=轻量字段＋limit/offset 分页，收藏置顶→浏览次数降序）+ 计数 +
 * 单条 / 浏览次数 +1 / 收藏标记 / 新建 / 编辑 / 删除 / JSON 文本导入 / 导出。
 * 列表与单条对「系统预置 + 自有」合并返回；预置的改删被服务层拦截（只读）。
 */
@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @GetMapping
    public Result<List<Food>> list(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Boolean lite,
                                   @RequestParam(required = false) Integer limit,
                                   @RequestParam(required = false) Integer offset) {
        Long userId = UserContext.requireUserId();
        if (Boolean.TRUE.equals(lite)) {
            return Result.success(foodService.listLite(userId, q, limit, offset));
        }
        return Result.success(foodService.list(userId, q));
    }

    @GetMapping("/count")
    public Result<Map<String, Long>> count(@RequestParam(required = false) String q) {
        return Result.success(foodService.count(UserContext.requireUserId(), q));
    }

    @GetMapping("/{id}")
    public Result<Food> get(@PathVariable Long id) {
        return Result.success(foodService.get(UserContext.requireUserId(), id));
    }

    /** 浏览次数 +1（食物详情页查看时调用；预置与自有均可）。 */
    @PostMapping("/{id}/view")
    public Result<Void> view(@PathVariable Long id) {
        foodService.incrementView(UserContext.requireUserId(), id);
        return Result.success();
    }

    /** 收藏 / 取消收藏（收藏＝食物库列表置顶；预置与自有均可）。 */
    @PutMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id, @RequestParam Boolean favorite) {
        foodService.setFavorite(UserContext.requireUserId(), id, Boolean.TRUE.equals(favorite));
        return Result.success();
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
