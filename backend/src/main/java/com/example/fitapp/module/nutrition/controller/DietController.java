package com.example.fitapp.module.nutrition.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.nutrition.dto.MealBatchDTO;
import com.example.fitapp.module.nutrition.dto.MealLogDTO;
import com.example.fitapp.module.nutrition.dto.MealUpdateDTO;
import com.example.fitapp.module.nutrition.dto.WaterDTO;
import com.example.fitapp.module.nutrition.entity.MealPhoto;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.nutrition.vo.DayOverviewVO;
import com.example.fitapp.module.nutrition.vo.MealItemVO;
import com.example.fitapp.module.nutrition.vo.StorageVO;
import com.example.fitapp.module.nutrition.vo.TrendVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 饮食记录接口：当日总览 / 条目增改删 / 每餐照片（上传即替换、读取直出图片）/ 饮水 / 趋势。
 */
@RestController
@RequestMapping("/api/diet")
@RequiredArgsConstructor
public class DietController {

    private final DietService dietService;

    @GetMapping
    public Result<DayOverviewVO> day(@RequestParam String date) {
        return Result.success(dietService.day(UserContext.requireUserId(), date));
    }

    @PostMapping("/meals")
    public Result<MealItemVO> addMeal(@Valid @RequestBody MealLogDTO body) {
        return Result.success(dietService.addMeal(UserContext.requireUserId(), body));
    }

    /** 批量加条目（食物组合「整份加入」用）：一次事务落多条，返回新增的条目。 */
    @PostMapping("/meals/batch")
    public Result<List<MealItemVO>> addMealsBatch(@Valid @RequestBody MealBatchDTO body) {
        return Result.success(dietService.addMealsBatch(UserContext.requireUserId(), body));
    }

    @PutMapping("/meals/{id}")
    public Result<MealItemVO> updateMeal(@PathVariable Long id, @Valid @RequestBody MealUpdateDTO body) {
        return Result.success(dietService.updateMeal(UserContext.requireUserId(), id, body));
    }

    @DeleteMapping("/meals/{id}")
    public Result<Void> deleteMeal(@PathVariable Long id) {
        dietService.deleteMeal(UserContext.requireUserId(), id);
        return Result.success();
    }

    /** 上传/替换某餐照片（multipart；字段名 file）。 */
    @PostMapping("/photos")
    public Result<Void> uploadPhoto(@RequestParam String date,
                                    @RequestParam String meal,
                                    @RequestParam("file") MultipartFile file) {
        dietService.savePhoto(UserContext.requireUserId(), date, meal, file);
        return Result.success();
    }

    /** 直出图片二进制（不走 Result 信封）；前端带 token fetch → blob 展示。 */
    @GetMapping("/photos/{date}/{meal}")
    public ResponseEntity<byte[]> photo(@PathVariable String date, @PathVariable String meal) {
        DietService.PhotoData d = dietService.readPhoto(UserContext.requireUserId(), date, meal, false);
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mime()))
                .contentLength(d.data().length)
                // 照片会被替换——客户端不存缓存，每次取新图
                .cacheControl(CacheControl.noStore())
                .body(d.data());
    }

    /** 相册列表（按日期倒序、同日早→晚；只出元数据，图片数据由前端逐个取）。 */
    @GetMapping("/photos")
    public Result<List<MealPhoto>> album(@RequestParam(required = false) Integer offset,
                                         @RequestParam(required = false) Integer limit) {
        return Result.success(dietService.album(UserContext.requireUserId(), offset, limit));
    }

    /** 缩略图（相册与列表用；无缩略图的老数据回退原图）。 */
    @GetMapping("/photos/{date}/{meal}/thumb")
    public ResponseEntity<byte[]> photoThumb(@PathVariable String date, @PathVariable String meal) {
        DietService.PhotoData d = dietService.readPhoto(UserContext.requireUserId(), date, meal, true);
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mime()))
                .contentLength(d.data().length)
                .cacheControl(CacheControl.noStore())
                .body(d.data());
    }

    /** 删除某餐照片（不可恢复）。 */
    @DeleteMapping("/photos/{date}/{meal}")
    public Result<Void> deletePhoto(@PathVariable String date, @PathVariable String meal) {
        dietService.deletePhoto(UserContext.requireUserId(), date, meal);
        return Result.success();
    }

    /** 存储占用（ADR 0013）：目前只统计图片一类（已用 / 张数 / 上限）。 */
    @GetMapping("/storage")
    public Result<StorageVO> storage() {
        return Result.success(dietService.storage(UserContext.requireUserId()));
    }

    /** 饮水加减一笔，返回该日最新累计（ml）。 */
    @PostMapping("/water")
    public Result<Integer> water(@Valid @RequestBody WaterDTO body) {
        return Result.success(dietService.addWater(UserContext.requireUserId(), body));
    }

    /**
     * 趋势。给了 from/to 就按起止日期取；都没给则自动从**最早一条记录**起（ADR 0015，
     * 前端一次拉全量、在本地缩放平移）。兼容旧的 days 参数（无历史时按最近 days 天）。
     */
    @GetMapping("/trend")
    public Result<TrendVO> trend(@RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to,
                                 @RequestParam(required = false) Integer days) {
        return Result.success(dietService.trend(UserContext.requireUserId(), from, to, days));
    }
}
