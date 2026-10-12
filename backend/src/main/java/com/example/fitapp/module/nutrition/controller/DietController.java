package com.example.fitapp.module.nutrition.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.nutrition.dto.MealLogDTO;
import com.example.fitapp.module.nutrition.dto.MealUpdateDTO;
import com.example.fitapp.module.nutrition.dto.WaterDTO;
import com.example.fitapp.module.nutrition.entity.MealPhoto;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.nutrition.vo.DayOverviewVO;
import com.example.fitapp.module.nutrition.vo.MealItemVO;
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
        MealPhoto p = dietService.getPhoto(UserContext.requireUserId(), date, meal);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getMime()))
                .contentLength(p.getImage().length)
                // 照片会被替换——客户端不存缓存，每次取新图
                .cacheControl(CacheControl.noStore())
                .body(p.getImage());
    }

    /** 饮水加减一笔，返回该日最新累计（ml）。 */
    @PostMapping("/water")
    public Result<Integer> water(@Valid @RequestBody WaterDTO body) {
        return Result.success(dietService.addWater(UserContext.requireUserId(), body));
    }

    @GetMapping("/trend")
    public Result<TrendVO> trend(@RequestParam(required = false) Integer days) {
        return Result.success(dietService.trend(UserContext.requireUserId(), days));
    }
}
