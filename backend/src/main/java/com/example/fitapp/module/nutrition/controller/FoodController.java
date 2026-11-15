package com.example.fitapp.module.nutrition.controller;

import com.example.fitapp.common.PhotoData;
import com.example.fitapp.common.Result;
import com.example.fitapp.module.account.dto.CropDTO;
import com.example.fitapp.module.nutrition.dto.FoodImportDTO;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.service.FoodImageService;
import com.example.fitapp.module.nutrition.service.FoodService;
import com.example.fitapp.module.nutrition.vo.ImportResultVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 食物库接口：列表（搜索匹配名字和别名；lite=轻量字段＋limit/offset 分页；排序见 ADR 0056）+ 计数 +
 * 单条 / 浏览次数 +1 / 收藏标记 / 新建 / 编辑 / 删除 / JSON 文本导入 / 导出 / 食物图片（ADR 0057）。
 * 列表与单条对「系统预置 + 自有」合并返回；预置的改删被服务层拦截（只读）。
 */
@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;
    private final FoodImageService foodImageService;

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
    public Result<Food> create(@Validated({Default.class, FoodSaveDTO.OwnFood.class}) @RequestBody FoodSaveDTO body) {
        return Result.success(foodService.create(UserContext.requireUserId(), body));
    }

    @PutMapping("/{id}")
    public Result<Food> update(@PathVariable Long id,
                               @Validated({Default.class, FoodSaveDTO.OwnFood.class}) @RequestBody FoodSaveDTO body) {
        return Result.success(foodService.update(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        foodService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }

    /** 设食物图片（multipart：file ＋ 裁剪框 x、y、size，按摆正后的原图像素；不给框就居中取短边）。只限自建食物。 */
    @PostMapping("/{id}/image")
    public Result<Food> setImage(@PathVariable Long id,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) Integer x,
                                 @RequestParam(required = false) Integer y,
                                 @RequestParam(required = false) Integer size) {
        return Result.success(foodImageService.setOwn(UserContext.requireUserId(), id, file, crop(x, y, size)));
    }

    /** 去掉食物图片（旧图直接删，不进回收站）。只限自建食物。 */
    @DeleteMapping("/{id}/image")
    public Result<Void> removeImage(@PathVariable Long id) {
        foodImageService.removeOwn(UserContext.requireUserId(), id);
        return Result.success();
    }

    /** 直出食物图片；kind＝thumb 取缩略图，否则取方图。预置与自有均可取；没有图 → 404。 */
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id, @RequestParam(defaultValue = "full") String kind) {
        return PhotoData.response(foodImageService.image(UserContext.requireUserId(), id, kind));
    }

    static CropDTO crop(Integer x, Integer y, Integer size) {
        CropDTO c = new CropDTO();
        c.setX(x);
        c.setY(y);
        c.setSize(size);
        return c;
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
