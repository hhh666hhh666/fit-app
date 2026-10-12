package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.nutrition.dto.MealLogDTO;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 营养快照（per100×grams÷100）、照片替换而非追加、饮水不为负、趋势缺日补零、越权拒绝。
 */
@ExtendWith(MockitoExtension.class)
class DietServiceTest {

    @Mock
    private MealLogMapper mealLogMapper;

    @Mock
    private MealPhotoMapper mealPhotoMapper;

    @Mock
    private DietDailyMapper dietDailyMapper;

    @Mock
    private FoodMapper foodMapper;

    @Mock
    private SettingService settingService;

    private DietService service() {
        return new DietService(mealLogMapper, mealPhotoMapper, dietDailyMapper, foodMapper, settingService);
    }

    private static Food chicken() {
        Food f = new Food();
        f.setId(5L);
        f.setUserId(7L);
        f.setName("鸡胸肉（生）");
        f.setKcal(new BigDecimal("118"));
        f.setProtein(new BigDecimal("24.6"));
        f.setCarb(new BigDecimal("0.6"));
        f.setFat(new BigDecimal("1.9"));
        return f;
    }

    private static MealLog log(String meal, String name, String grams, String kcal, String protein,
                               String carb, String fat) {
        MealLog l = new MealLog();
        l.setUserId(7L);
        l.setMeal(meal);
        l.setFoodName(name);
        l.setGrams(new BigDecimal(grams));
        l.setKcalPer100(new BigDecimal(kcal));
        l.setProteinPer100(new BigDecimal(protein));
        l.setCarbPer100(new BigDecimal(carb));
        l.setFatPer100(new BigDecimal(fat));
        return l;
    }

    @Test
    void addMeal_snapshotsPer100AndComputesActual() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-10-12");
        dto.setMeal("lunch");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("200"));

        MealItemVO item = service().addMeal(7L, dto);

        ArgumentCaptor<MealLog> captor = ArgumentCaptor.forClass(MealLog.class);
        verify(mealLogMapper).insert(captor.capture());
        // 快照是「每 100 数值」+ 实际克数，而非折算后的实际值
        assertEquals(new BigDecimal("118"), captor.getValue().getKcalPer100());
        assertEquals(new BigDecimal("24.6"), captor.getValue().getProteinPer100());
        assertEquals(new BigDecimal("200"), captor.getValue().getGrams());
        // 实际值：118 × 200 ÷ 100 = 236.00
        assertEquals(new BigDecimal("236.00"), item.getKcal());
        assertEquals(new BigDecimal("49.20"), item.getProtein());
    }

    @Test
    void addMeal_foreignFood_throws() {
        Food other = chicken();
        other.setUserId(99L);
        when(foodMapper.selectById(5L)).thenReturn(other);
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-10-12");
        dto.setMeal("lunch");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("200"));

        assertThrows(BusinessException.class, () -> service().addMeal(7L, dto));
        verify(mealLogMapper, never()).insert(any(MealLog.class));
    }

    @Test
    void deleteMeal_notOwn_throws() {
        MealLog other = log("lunch", "米饭", "100", "116", "2.6", "25.9", "0.3");
        other.setUserId(99L);
        when(mealLogMapper.selectById(3L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service().deleteMeal(7L, 3L));
        verify(mealLogMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void savePhoto_replacesExistingInsteadOfAppending() {
        MealPhoto existing = new MealPhoto();
        existing.setId(3L);
        existing.setUserId(7L);
        when(mealPhotoMapper.selectOne(any())).thenReturn(existing);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        service().savePhoto(7L, "2026-10-12", "lunch", file);

        verify(mealPhotoMapper).updateById(existing);
        verify(mealPhotoMapper, never()).insert(any(MealPhoto.class));
        assertEquals("image/jpeg", existing.getMime());
        assertEquals(3, existing.getSizeBytes());
    }

    @Test
    void savePhoto_insertsWhenAbsent() {
        when(mealPhotoMapper.selectOne(any())).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{9});

        service().savePhoto(7L, "2026-10-12", "dinner", file);

        ArgumentCaptor<MealPhoto> captor = ArgumentCaptor.forClass(MealPhoto.class);
        verify(mealPhotoMapper).insert(captor.capture());
        assertEquals("dinner", captor.getValue().getMeal());
        assertEquals("image/png", captor.getValue().getMime());
    }

    @Test
    void savePhoto_badMime_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});
        assertThrows(BusinessException.class, () -> service().savePhoto(7L, "2026-10-12", "lunch", file));
        verify(mealPhotoMapper, never()).insert(any(MealPhoto.class));
    }

    @Test
    void addWater_clampsAtZero() {
        DietDaily dd = new DietDaily();
        dd.setId(1L);
        dd.setUserId(7L);
        dd.setWaterMl(100);
        when(dietDailyMapper.selectOne(any())).thenReturn(dd);
        WaterDTO dto = new WaterDTO();
        dto.setDate("2026-10-12");
        dto.setDelta(-500);

        int now = service().addWater(7L, dto);

        assertEquals(0, now);
        verify(dietDailyMapper).updateById(dd);
    }

    @Test
    void addWater_insertsWhenAbsent() {
        when(dietDailyMapper.selectOne(any())).thenReturn(null);
        WaterDTO dto = new WaterDTO();
        dto.setDate("2026-10-12");
        dto.setDelta(250);

        int now = service().addWater(7L, dto);

        assertEquals(250, now);
        ArgumentCaptor<DietDaily> captor = ArgumentCaptor.forClass(DietDaily.class);
        verify(dietDailyMapper).insert(captor.capture());
        assertEquals(250, captor.getValue().getWaterMl());
    }

    @Test
    void trend_fillsGapsWithZero() {
        LocalDate today = LocalDate.now();
        DailyNutritionRow row = new DailyNutritionRow();
        row.setLogDate(today);
        row.setKcal(new BigDecimal("500.00"));
        row.setProtein(new BigDecimal("40.00"));
        row.setCarb(new BigDecimal("50.00"));
        row.setFat(new BigDecimal("20.00"));
        when(mealLogMapper.selectDailyTotals(eq(7L), any(), any())).thenReturn(List.of(row));
        DietDaily dd = new DietDaily();
        dd.setLogDate(today);
        dd.setWaterMl(1500);
        when(dietDailyMapper.selectRange(eq(7L), any(), any())).thenReturn(List.of(dd));

        TrendVO vo = service().trend(7L, 30);

        assertEquals(30, vo.getDays().size());
        // 缺日补零（第一天距今 29 天，没有数据）
        assertEquals(BigDecimal.ZERO, vo.getDays().get(0).getKcal());
        assertEquals(0, vo.getDays().get(0).getWater());
        // 今天有数据
        assertEquals(new BigDecimal("500.00"), vo.getDays().get(29).getKcal());
        assertEquals(1500, vo.getDays().get(29).getWater());
    }

    @Test
    void day_aggregatesTotalsAndKeepsMealOrder() {
        MealLog dinner = log("dinner", "米饭", "150", "116", "2.6", "25.9", "0.3");
        dinner.setId(1L);
        MealLog breakfast = log("breakfast", "鸡蛋", "50", "139", "13.1", "2.4", "8.6");
        breakfast.setId(2L);
        when(mealLogMapper.selectList(any())).thenReturn(List.of(dinner, breakfast));
        when(dietDailyMapper.selectOne(any())).thenReturn(null);
        when(mealPhotoMapper.selectMealsWithPhoto(eq(7L), any())).thenReturn(List.of("breakfast"));
        SettingVO s = new SettingVO();
        s.setTargetKcal(new BigDecimal("2200"));
        s.setTargetWater(2000);
        when(settingService.get(7L)).thenReturn(s);

        DayOverviewVO vo = service().day(7L, "2026-10-12");

        // 四组固定且按 早/午/晚/加餐 排序（输入乱序也不受影响）
        assertEquals(4, vo.getMeals().size());
        assertEquals("breakfast", vo.getMeals().get(0).getMeal());
        assertEquals("鸡蛋", vo.getMeals().get(0).getItems().get(0).getFoodName());
        // 合计：米饭 116×150÷100=174.00 + 鸡蛋 139×50÷100=69.50 = 243.50
        assertEquals(new BigDecimal("243.50"), vo.getTotals().getKcal());
        assertEquals(0, vo.getWater().getMl());
        assertEquals(2000, vo.getWater().getTarget());
        assertEquals(List.of("breakfast"), vo.getPhotoMeals());
    }
}
