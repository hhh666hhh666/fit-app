package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.diary.mapper.DiaryPhotoMapper;
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
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.nutrition.vo.DailyNutritionRow;
import com.example.fitapp.module.nutrition.vo.DayOverviewVO;
import com.example.fitapp.module.nutrition.vo.MealItemVO;
import com.example.fitapp.module.nutrition.vo.SettingVO;
import com.example.fitapp.module.nutrition.vo.TrendVO;
import com.example.fitapp.module.profile.service.EnergyService;
import com.example.fitapp.module.profile.vo.EnergyVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private PhotoStorage photoStorage;

    @Mock
    private EnergyService energyService;

    @Mock
    private DiaryPhotoMapper diaryPhotoMapper;

    /** 单测里 @Value 不生效：手动给一个够大的额度（容量拒绝另有用例）。 */
    private DietService service() {
        DietService s = new DietService(mealLogMapper, mealPhotoMapper, dietDailyMapper, foodMapper,
                settingService, energyService, photoStorage, diaryPhotoMapper);
        ReflectionTestUtils.setField(s, "photoQuotaBytes", 20L * 1024 * 1024 * 1024);
        return s;
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

    /** 首次写入其实成功、只是响应阶段才断连时，重试查到同路径的行就不再插入（ADR 0045）。 */
    @Test
    void savePhoto_retryFindsSamePath_doesNotInsertAgain() {
        MealPhoto existing = new MealPhoto();
        existing.setId(3L);
        existing.setUserId(7L);
        when(mealPhotoMapper.selectOne(any())).thenReturn(existing);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertEquals(3L, service().savePhoto(7L, "2026-10-12", "lunch", file));

        verify(mealPhotoMapper, never()).insert(any(MealPhoto.class));
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

    /**
     * ADR 0026 的核心契约：图片**落盘**、库里**只留路径**。
     * 这条用例守的是「别再往库里塞 BLOB」——那正是备份做不下去的根因。
     */
    @Test
    void savePhoto_writesFileAndKeepsOnlyPathInDb() throws Exception {
        when(mealPhotoMapper.selectOne(any())).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        service().savePhoto(7L, "2026-10-12", "lunch", file);

        ArgumentCaptor<MealPhoto> captor = ArgumentCaptor.forClass(MealPhoto.class);
        verify(mealPhotoMapper).insert(captor.capture());
        // 一餐可以有多张（ADR 0045）：路径带随机串，新图不会覆盖同餐的其他图
        String path = captor.getValue().getPath();
        assertTrue(path.startsWith("7/2026-10-12/lunch-"));
        assertNull(captor.getValue().getImage());
        verify(photoStorage).write(eq(path), eq("image/jpeg"), any(), any());
    }

    /** 删除要先删行、后删文件——反过来的失败态是「记录指向不存在的图」，更难收拾。 */
    @Test
    void deletePhoto_removesRowThenFile() {
        MealPhoto p = new MealPhoto();
        p.setId(3L);
        p.setUserId(7L);
        p.setPath("7/2026-10-12/lunch");
        when(mealPhotoMapper.selectOne(any())).thenReturn(p);

        service().deletePhoto(7L, 3L);

        verify(mealPhotoMapper).deleteById(3L);
        verify(photoStorage).delete("7/2026-10-12/lunch");
    }

    @Test
    void savePhoto_badMime_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});
        assertThrows(BusinessException.class, () -> service().savePhoto(7L, "2026-10-12", "lunch", file));
        verify(mealPhotoMapper, never()).insert(any(MealPhoto.class));
    }

    @Test
    void savePhoto_overQuota_throws() {
        DietService s = new DietService(mealLogMapper, mealPhotoMapper, dietDailyMapper, foodMapper,
                settingService, energyService, photoStorage, diaryPhotoMapper);
        // 额度只剩 2 字节，新图 3 字节 → 必须拒绝（ADR 0013）
        ReflectionTestUtils.setField(s, "photoQuotaBytes", 2L);
        when(mealPhotoMapper.selectOne(any())).thenReturn(null);
        when(mealPhotoMapper.sumBytes(7L)).thenReturn(0L);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThrows(BusinessException.class, () -> s.savePhoto(7L, "2026-10-12", "lunch", file));
        verify(mealPhotoMapper, never()).insert(any(MealPhoto.class));
        // 容量拒绝是业务性失败，不该被重试——TransientRetry 只认可重试的那一族
        verify(mealPhotoMapper, times(1)).selectOne(any());
    }

    @Test
    void savePhoto_retriesOnTransientDbFailure() {
        // 共用 MySQL 容器被 OOM-killer 杀掉重启时，insert 抛的就是这一类（线上实测是
        // RecoverableDataAccessException 包着 MySQL 的 CommunicationsException）。重试应能自愈。
        when(mealPhotoMapper.selectOne(any())).thenReturn(null);
        when(mealPhotoMapper.insert(any(MealPhoto.class)))
                .thenThrow(new RecoverableDataAccessException("Communications link failure"))
                .thenReturn(1);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        service().savePhoto(7L, "2026-10-12", "lunch", file);

        verify(mealPhotoMapper, times(2)).insert(any(MealPhoto.class));
    }

    @Test
    void savePhoto_givesUpAfterRetriesExhausted() {
        when(mealPhotoMapper.selectOne(any())).thenReturn(null);
        when(mealPhotoMapper.insert(any(MealPhoto.class)))
                .thenThrow(new RecoverableDataAccessException("Communications link failure"));
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        // 重试用尽后抛**原异常**，由 GlobalExceptionHandler 统一转成「服务正在重启，请稍后重试」
        assertThrows(RecoverableDataAccessException.class,
                () -> service().savePhoto(7L, "2026-10-12", "lunch", file));
        verify(mealPhotoMapper, times(3)).insert(any(MealPhoto.class));
    }

    @Test
    void deletePhoto_absent_throws() {
        // 实现改成「先查出该行（拿 path）再删」，所以这里要桩 selectOne 而不是 delete
        when(mealPhotoMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service().deletePhoto(7L, 3L));
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

        TrendVO vo = service().trend(7L, null, null, 30);

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

    @Test
    void day_gapIsTotalBurnMinusIntake() {
        MealLog breakfast = log("breakfast", "鸡蛋", "100", "100", "10", "1", "8");
        breakfast.setId(1L);
        when(mealLogMapper.selectList(any())).thenReturn(List.of(breakfast));
        when(dietDailyMapper.selectOne(any())).thenReturn(null);
        when(mealPhotoMapper.selectMealsWithPhoto(eq(7L), any())).thenReturn(List.of());
        when(settingService.get(7L)).thenReturn(new SettingVO());
        EnergyVO energy = new EnergyVO();
        energy.setComplete(true);
        energy.setTotalKcal(2500);
        when(energyService.compute(eq(7L), any())).thenReturn(energy);

        DayOverviewVO vo = service().day(7L, "2026-10-12");

        // 摄入 100，总消耗 2500 → 缺口 2400（正数＝缺口，展示时按正数念，ADR 0029）
        assertEquals(2400, vo.getGap());
    }

    @Test
    void day_gapIsNullWhenProfileIncomplete() {
        when(mealLogMapper.selectList(any())).thenReturn(List.of());
        when(dietDailyMapper.selectOne(any())).thenReturn(null);
        when(mealPhotoMapper.selectMealsWithPhoto(eq(7L), any())).thenReturn(List.of());
        when(settingService.get(7L)).thenReturn(new SettingVO());
        EnergyVO energy = new EnergyVO();
        energy.setComplete(false);
        energy.setMissing("profile");
        when(energyService.compute(eq(7L), any())).thenReturn(energy);

        DayOverviewVO vo = service().day(7L, "2026-10-12");

        // 算不出消耗就不给缺口数字——不拿兜底值造一个看着精确的假数
        assertNull(vo.getGap());
        assertEquals("profile", vo.getEnergy().getMissing());
    }
}
