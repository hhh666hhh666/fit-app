package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.nutrition.dto.MealLogDTO;
import com.example.fitapp.module.nutrition.dto.MealUpdateDTO;
import com.example.fitapp.module.nutrition.dto.WaterDTO;
import com.example.fitapp.module.nutrition.entity.DietDaily;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.entity.MealPhoto;
import com.example.fitapp.module.nutrition.mapper.DietDailyMapper;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
import com.example.fitapp.module.nutrition.mapper.MealPhotoMapper;
import com.example.fitapp.module.nutrition.vo.DailyNutritionRow;
import com.example.fitapp.module.nutrition.vo.DayOverviewVO;
import com.example.fitapp.module.nutrition.vo.MealItemVO;
import com.example.fitapp.module.nutrition.vo.SettingVO;
import com.example.fitapp.module.nutrition.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 饮食记录：当日总览 / 条目增改删 / 每餐照片（上传即替换）/ 饮水加减 / 趋势。
 * 营养一律按条目快照（per100 × grams ÷ 100）现算；目标与饮水步进来自设置。
 */
@Service
@RequiredArgsConstructor
public class DietService {

    private static final List<String> MEAL_ORDER = List.of("breakfast", "lunch", "dinner", "snack");
    private static final Set<String> PHOTO_MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final MealLogMapper mealLogMapper;
    private final MealPhotoMapper mealPhotoMapper;
    private final DietDailyMapper dietDailyMapper;
    private final FoodMapper foodMapper;
    private final SettingService settingService;

    // ── 当日总览 ──

    public DayOverviewVO day(Long userId, String dateStr) {
        LocalDate date = parseDate(dateStr);
        List<MealLog> logs = mealLogMapper.selectList(Wrappers.<MealLog>lambdaQuery()
                .eq(MealLog::getUserId, userId)
                .eq(MealLog::getLogDate, date)
                .orderByAsc(MealLog::getMeal)
                .orderByAsc(MealLog::getId));

        DayOverviewVO vo = new DayOverviewVO();
        vo.setDate(date.toString());

        DayOverviewVO.Totals totals = new DayOverviewVO.Totals();
        BigDecimal kcal = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carb = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        for (MealLog l : logs) {
            kcal = kcal.add(actual(l.getKcalPer100(), l.getGrams()));
            protein = protein.add(actual(l.getProteinPer100(), l.getGrams()));
            carb = carb.add(actual(l.getCarbPer100(), l.getGrams()));
            fat = fat.add(actual(l.getFatPer100(), l.getGrams()));
        }
        totals.setKcal(kcal);
        totals.setProtein(protein);
        totals.setCarb(carb);
        totals.setFat(fat);
        vo.setTotals(totals);

        // 餐次固定四条（保持渲染顺序）
        Map<String, List<MealItemVO>> byMeal = logs.stream().collect(Collectors.groupingBy(
                MealLog::getMeal, LinkedHashMap::new,
                Collectors.mapping(this::toItem, Collectors.toList())));
        List<DayOverviewVO.MealGroup> groups = new ArrayList<>();
        for (String m : MEAL_ORDER) {
            DayOverviewVO.MealGroup g = new DayOverviewVO.MealGroup();
            g.setMeal(m);
            g.setItems(byMeal.getOrDefault(m, List.of()));
            groups.add(g);
        }
        vo.setMeals(groups);

        // 饮水 + 目标（设置行不存在时给默认）
        SettingVO setting = settingService.get(userId);
        DietDaily dd = dietDailyMapper.selectOne(Wrappers.<DietDaily>lambdaQuery()
                .eq(DietDaily::getUserId, userId)
                .eq(DietDaily::getLogDate, date));
        DayOverviewVO.Water water = new DayOverviewVO.Water();
        water.setMl(dd == null ? 0 : dd.getWaterMl());
        water.setTarget(setting.getTargetWater());
        vo.setWater(water);

        DayOverviewVO.Targets targets = new DayOverviewVO.Targets();
        targets.setKcal(setting.getTargetKcal());
        targets.setProtein(setting.getTargetProtein());
        targets.setCarb(setting.getTargetCarb());
        targets.setFat(setting.getTargetFat());
        targets.setWater(setting.getTargetWater());
        vo.setTargets(targets);

        vo.setPhotoMeals(mealPhotoMapper.selectMealsWithPhoto(userId, date));
        return vo;
    }

    // ── 条目 ──

    public MealItemVO addMeal(Long userId, MealLogDTO dto) {
        Food food = requireOwnFood(userId, dto.getFoodId());
        MealLog log = new MealLog();
        log.setUserId(userId);
        log.setLogDate(parseDate(dto.getDate()));
        log.setMeal(dto.getMeal());
        log.setFoodId(food.getId());
        log.setFoodName(food.getName());
        log.setGrams(dto.getGrams());
        // 每 100 数值快照：此后改库/删库不影响本条
        log.setKcalPer100(food.getKcal());
        log.setProteinPer100(food.getProtein());
        log.setCarbPer100(food.getCarb());
        log.setFatPer100(food.getFat());
        mealLogMapper.insert(log);
        return toItem(log);
    }

    public MealItemVO updateMeal(Long userId, Long id, MealUpdateDTO dto) {
        MealLog log = requireOwnMeal(userId, id);
        log.setGrams(dto.getGrams());
        mealLogMapper.updateById(log);
        return toItem(log);
    }

    public void deleteMeal(Long userId, Long id) {
        requireOwnMeal(userId, id);
        mealLogMapper.deleteById(id);
    }

    // ── 照片（每餐一张；上传即替换）──

    public void savePhoto(Long userId, String dateStr, String meal, MultipartFile file) {
        if (!MEAL_ORDER.contains(meal)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "餐次不正确");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "照片为空");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "照片超过 10MB");
        }
        String mime = file.getContentType();
        if (mime == null || !PHOTO_MIME.contains(mime)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只支持 JPEG/PNG/WebP 图片");
        }
        LocalDate date = parseDate(dateStr);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "照片读取失败");
        }

        MealPhoto existing = mealPhotoMapper.selectOne(Wrappers.<MealPhoto>lambdaQuery()
                .eq(MealPhoto::getUserId, userId)
                .eq(MealPhoto::getLogDate, date)
                .eq(MealPhoto::getMeal, meal));
        if (existing == null) {
            MealPhoto p = new MealPhoto();
            p.setUserId(userId);
            p.setLogDate(date);
            p.setMeal(meal);
            p.setMime(mime);
            p.setSizeBytes(bytes.length);
            p.setImage(bytes);
            mealPhotoMapper.insert(p);
        } else {
            existing.setMime(mime);
            existing.setSizeBytes(bytes.length);
            existing.setImage(bytes);
            mealPhotoMapper.updateById(existing);
        }
    }

    /** 读照片；不存在返回 null（由 Controller 转 404）。 */
    public MealPhoto getPhoto(Long userId, String dateStr, String meal) {
        LocalDate date = parseDate(dateStr);
        return mealPhotoMapper.selectOne(Wrappers.<MealPhoto>lambdaQuery()
                .eq(MealPhoto::getUserId, userId)
                .eq(MealPhoto::getLogDate, date)
                .eq(MealPhoto::getMeal, meal));
    }

    // ── 饮水 ──

    /** 加减一笔（结果不小于 0），返回该日最新累计。 */
    @Transactional
    public int addWater(Long userId, WaterDTO dto) {
        if (dto.getDelta() == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "增减量不能为 0");
        }
        LocalDate date = parseDate(dto.getDate());
        DietDaily dd = dietDailyMapper.selectOne(Wrappers.<DietDaily>lambdaQuery()
                .eq(DietDaily::getUserId, userId)
                .eq(DietDaily::getLogDate, date));
        if (dd == null) {
            dd = new DietDaily();
            dd.setUserId(userId);
            dd.setLogDate(date);
            dd.setWaterMl(Math.max(0, dto.getDelta()));
            dietDailyMapper.insert(dd);
        } else {
            dd.setWaterMl(Math.max(0, dd.getWaterMl() + dto.getDelta()));
            dietDailyMapper.updateById(dd);
        }
        return dd.getWaterMl();
    }

    // ── 趋势 ──

    public TrendVO trend(Long userId, Integer days) {
        int n = (days == null || days < 1) ? 30 : Math.min(days, 90);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(n - 1L);

        Map<LocalDate, DailyNutritionRow> byDate = mealLogMapper.selectDailyTotals(userId, from, to)
                .stream().collect(Collectors.toMap(DailyNutritionRow::getLogDate, r -> r));
        Map<LocalDate, Integer> waterByDate = dietDailyMapper.selectRange(userId, from, to).stream()
                .collect(Collectors.toMap(DietDaily::getLogDate, DietDaily::getWaterMl, (a, b) -> b));

        List<TrendVO.Point> points = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyNutritionRow r = byDate.get(d);
            TrendVO.Point p = new TrendVO.Point();
            p.setDate(d.toString());
            p.setKcal(r == null ? BigDecimal.ZERO : scale2(r.getKcal()));
            p.setProtein(r == null ? BigDecimal.ZERO : scale2(r.getProtein()));
            p.setCarb(r == null ? BigDecimal.ZERO : scale2(r.getCarb()));
            p.setFat(r == null ? BigDecimal.ZERO : scale2(r.getFat()));
            p.setWater(waterByDate.getOrDefault(d, 0));
            points.add(p);
        }
        TrendVO vo = new TrendVO();
        vo.setDays(points);
        return vo;
    }

    // ── 辅助 ──

    /** 实际摄入 = per100 × grams ÷ 100（四舍五入 2 位）。 */
    private static BigDecimal actual(BigDecimal per100, BigDecimal grams) {
        if (per100 == null || grams == null) {
            return BigDecimal.ZERO;
        }
        return per100.multiply(grams).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private MealItemVO toItem(MealLog l) {
        MealItemVO i = new MealItemVO();
        i.setId(l.getId());
        i.setMeal(l.getMeal());
        i.setFoodId(l.getFoodId());
        i.setFoodName(l.getFoodName());
        i.setGrams(l.getGrams());
        i.setKcal(actual(l.getKcalPer100(), l.getGrams()));
        i.setProtein(actual(l.getProteinPer100(), l.getGrams()));
        i.setCarb(actual(l.getCarbPer100(), l.getGrams()));
        i.setFat(actual(l.getFatPer100(), l.getGrams()));
        return i;
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "日期格式不正确");
        }
    }

    private Food requireOwnFood(Long userId, Long foodId) {
        Food food = foodMapper.selectById(foodId);
        // 允许系统预置（user_id 为空）：预置食物可直接被记录（快照照写）
        if (food == null || (food.getUserId() != null && !userId.equals(food.getUserId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "食物不存在");
        }
        return food;
    }

    private MealLog requireOwnMeal(Long userId, Long id) {
        MealLog log = mealLogMapper.selectById(id);
        if (log == null || !userId.equals(log.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return log;
    }
}
