package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.diary.mapper.DiaryPhotoMapper;
import com.example.fitapp.module.nutrition.dto.MealCustomDTO;
import com.example.fitapp.module.nutrition.dto.MealLogDTO;
import com.example.fitapp.module.nutrition.dto.MealUpdateDTO;
import com.example.fitapp.module.nutrition.dto.WaterDTO;
import com.example.fitapp.module.nutrition.entity.DietDaily;
import com.example.fitapp.module.nutrition.dto.WaterDrinkDTO;
import com.example.fitapp.module.nutrition.entity.WaterLog;
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
import com.example.fitapp.module.preset.service.PresetHiddenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.io.InputStreamSource;
import com.example.fitapp.module.nutrition.storage.PhotoUploadPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    /** 改时刻、清时刻走 lambda wrapper：单测里要先登记实体的表信息。 */
    @org.junit.jupiter.api.BeforeAll
    static void registerTableInfo() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MealLog.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, WaterLog.class);
    }

    @Mock
    private PresetHiddenService presetHiddenService;

    @Mock
    private TrashItemMapper trashItemMapper;
    @Mock
    private com.example.fitapp.module.account.mapper.AvatarMapper avatarMapper;

    @Mock
    private MealPhotoTrash mealPhotoTrash;

    @Mock
    private MealLogMapper mealLogMapper;

    @Mock
    private MealPhotoMapper mealPhotoMapper;

    @Mock
    private DietDailyMapper dietDailyMapper;

    @Mock
    private com.example.fitapp.module.nutrition.mapper.WaterLogMapper waterLogMapper;

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
        DietService s = new DietService(mealLogMapper, mealPhotoMapper, dietDailyMapper, waterLogMapper, foodMapper,
                presetHiddenService, settingService, energyService, photoStorage, new PhotoUploadPolicy(50), diaryPhotoMapper, avatarMapper, trashItemMapper, mealPhotoTrash);
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
    void addMeal_snapshotsSodiumToo() {
        Food f = chicken();
        f.setSodium(new BigDecimal("60"));
        when(foodMapper.selectById(5L)).thenReturn(f);
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-10-12");
        dto.setMeal("lunch");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("200"));

        MealItemVO item = service().addMeal(7L, dto);

        ArgumentCaptor<MealLog> captor = ArgumentCaptor.forClass(MealLog.class);
        verify(mealLogMapper).insert(captor.capture());
        assertEquals(new BigDecimal("60"), captor.getValue().getSodiumPer100());
        assertEquals(new BigDecimal("120.00"), item.getSodium());
    }

    @Test
    void addCustomMeal_noFoodIdAndSnapshotsWhatWasTyped() {
        MealCustomDTO dto = new MealCustomDTO();
        dto.setDate("2026-11-09");
        dto.setMeal("dinner");
        dto.setName("  番茄炒蛋盖饭 ");
        dto.setGrams(new BigDecimal("400"));
        dto.setKcal(new BigDecimal("150"));
        dto.setProtein(new BigDecimal("5"));
        dto.setFat(new BigDecimal("6"));
        dto.setCarb(new BigDecimal("20"));

        MealItemVO item = service().addCustomMeal(7L, dto);

        ArgumentCaptor<MealLog> captor = ArgumentCaptor.forClass(MealLog.class);
        verify(mealLogMapper).insert(captor.capture());
        MealLog row = captor.getValue();
        // 临时条目不挂食物库：food_id 为空，营养全在快照里
        assertNull(row.getFoodId());
        assertEquals("番茄炒蛋盖饭", row.getFoodName());
        assertEquals(LocalDate.of(2026, 11, 9), row.getLogDate());
        assertEquals(new BigDecimal("150"), row.getKcalPer100());
        assertNull(row.getSodiumPer100());
        // 150 × 400 ÷ 100 = 600；钠没填 → null，不当 0
        assertEquals(new BigDecimal("600.00"), item.getKcal());
        assertNull(item.getFoodId());
        assertNull(item.getSodium());
    }

    @Test
    void addMeal_byPortionFreezesUnitAndPieceGrams() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-11-23");
        dto.setMeal("dinner");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("60"));
        dto.setPortionUnit(" 个 ");
        dto.setPortionGrams(new BigDecimal("20"));

        MealItemVO item = service().addMeal(7L, dto);

        assertEquals("个", item.getPortionUnit());
        assertEquals(new BigDecimal("20"), item.getPortionGrams());
    }

    @Test
    void addMeal_unitWithoutPieceGramsCountsAsGrams() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-11-23");
        dto.setMeal("dinner");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("60"));
        dto.setPortionUnit("个");

        assertNull(service().addMeal(7L, dto).getPortionUnit());
    }

    @Test
    void addCounted_writesFoodAndTempWithPortionAndSharedTime() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        com.example.fitapp.module.nutrition.dto.MealCountItemDTO food = new com.example.fitapp.module.nutrition.dto.MealCountItemDTO();
        food.setFoodId(5L);
        food.setGrams(new BigDecimal("208"));
        food.setPortionUnit("个");
        food.setPortionGrams(new BigDecimal("20.8"));
        com.example.fitapp.module.nutrition.dto.MealCountItemDTO tmp = new com.example.fitapp.module.nutrition.dto.MealCountItemDTO();
        tmp.setName("猪肉丸");
        tmp.setKcal(new BigDecimal("196"));
        tmp.setProtein(new BigDecimal("14.5"));
        tmp.setFat(new BigDecimal("13"));
        tmp.setCarb(new BigDecimal("5"));
        tmp.setGrams(new BigDecimal("60"));
        tmp.setPortionUnit("颗");
        tmp.setPortionGrams(new BigDecimal("20"));
        com.example.fitapp.module.nutrition.dto.MealCountDTO dto = new com.example.fitapp.module.nutrition.dto.MealCountDTO();
        dto.setDate("2026-11-23");
        dto.setMeal("snack");
        dto.setTime("19:05");
        dto.setItems(List.of(food, tmp));

        List<MealItemVO> out = service().addCounted(7L, dto);

        assertEquals(2, out.size());
        assertEquals("19:05", out.get(0).getTime());
        assertEquals("19:05", out.get(1).getTime());
        assertEquals(5L, out.get(0).getFoodId());
        assertEquals("个", out.get(0).getPortionUnit());
        assertTrue(out.get(1).isTemporary());
        assertEquals("颗", out.get(1).getPortionUnit());
        // 196 × 60 ÷ 100 = 117.60
        assertEquals(new BigDecimal("117.60"), out.get(1).getKcal());
    }

    @Test
    void addCounted_tempWithoutNutritionThrows() {
        com.example.fitapp.module.nutrition.dto.MealCountItemDTO tmp = new com.example.fitapp.module.nutrition.dto.MealCountItemDTO();
        tmp.setName("猪肉丸");
        tmp.setGrams(new BigDecimal("60"));
        tmp.setPortionUnit("颗");
        tmp.setPortionGrams(new BigDecimal("20"));
        com.example.fitapp.module.nutrition.dto.MealCountDTO dto = new com.example.fitapp.module.nutrition.dto.MealCountDTO();
        dto.setDate("2026-11-23");
        dto.setMeal("snack");
        dto.setItems(List.of(tmp));

        assertThrows(BusinessException.class, () -> service().addCounted(7L, dto));
        verify(mealLogMapper, never()).insert(any(MealLog.class));
    }

    @Test
    void updateMeal_byGramsClearsPortionSnapshot() {
        MealLog l = log("lunch", "饺子", "200", "250", "10", "30", "10");
        l.setId(3L);
        l.setFoodId(5L);
        l.setPortionUnit("个");
        l.setPortionGrams(new BigDecimal("20"));
        when(mealLogMapper.selectById(3L)).thenReturn(l);
        MealUpdateDTO dto = new MealUpdateDTO();
        dto.setGrams(new BigDecimal("180"));

        MealItemVO item = service().updateMeal(7L, 3L, dto);

        assertNull(item.getPortionUnit());
        verify(mealLogMapper).update(any(), any());
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
    void updateMeal_renamesTemporaryEntry() {
        MealLog l = log("lunch", "红烧肉", "150", "300", "10", "5", "25");
        l.setId(3L);
        when(mealLogMapper.selectById(3L)).thenReturn(l);
        MealUpdateDTO dto = new MealUpdateDTO();
        dto.setName("  妈妈做的红烧肉 ");

        MealItemVO item = service().updateMeal(7L, 3L, dto);

        assertEquals("妈妈做的红烧肉", item.getFoodName());
        assertTrue(item.isTemporary());
        assertEquals(new BigDecimal("150"), item.getGrams(), "没给份量就不动");
    }

    @Test
    void updateMeal_libraryEntryCannotBeRenamed() {
        MealLog l = log("lunch", "米饭", "100", "116", "2.6", "25.9", "0.3");
        l.setFoodId(5L);
        when(mealLogMapper.selectById(3L)).thenReturn(l);
        MealUpdateDTO dto = new MealUpdateDTO();
        dto.setName("白米饭");

        assertThrows(BusinessException.class, () -> service().updateMeal(7L, 3L, dto));
        verify(mealLogMapper, never()).updateById(any(MealLog.class));
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
        verify(photoStorage).write(eq(path), eq("image/jpeg"), any(InputStreamSource.class), any());
    }

    /** 删除进回收站（ADR 0047）：删行，但文件要留着，恢复时才找得回来。 */
    @Test
    void deletePhoto_movesToTrashAndKeepsFile() {
        MealPhoto p = new MealPhoto();
        p.setId(3L);
        p.setUserId(7L);
        p.setPath("7/2026-10-12/lunch");
        when(mealPhotoMapper.selectOne(any())).thenReturn(p);

        service().deletePhoto(7L, 3L);

        verify(mealPhotoTrash).put(7L, p);
        verify(mealPhotoMapper).deleteById(3L);
        verify(photoStorage, never()).delete(any());
    }

    @Test
    void savePhoto_badMime_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});
        assertThrows(BusinessException.class, () -> service().savePhoto(7L, "2026-10-12", "lunch", file));
        verify(mealPhotoMapper, never()).insert(any(MealPhoto.class));
    }

    @Test
    void savePhoto_overQuota_throws() {
        DietService s = new DietService(mealLogMapper, mealPhotoMapper, dietDailyMapper, waterLogMapper, foodMapper,
                presetHiddenService, settingService, energyService, photoStorage, new PhotoUploadPolicy(50), diaryPhotoMapper, avatarMapper, trashItemMapper, mealPhotoTrash);
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
    void addWater_positiveAddsOneDrinkAndSyncsDailyTotal() {
        when(waterLogMapper.sumOfDay(eq(7L), any())).thenReturn(250);
        when(dietDailyMapper.selectOne(any())).thenReturn(null);
        WaterDTO dto = new WaterDTO();
        dto.setDate("2026-10-12");
        dto.setDelta(250);

        int now = service().addWater(7L, dto);

        assertEquals(250, now);
        ArgumentCaptor<WaterLog> drink = ArgumentCaptor.forClass(WaterLog.class);
        verify(waterLogMapper).insert(drink.capture());
        assertEquals(250, drink.getValue().getMl());
        assertNotNull(drink.getValue().getDrunkAt(), "饮水不给时刻就用现在的钟点");
        ArgumentCaptor<DietDaily> daily = ArgumentCaptor.forClass(DietDaily.class);
        verify(dietDailyMapper).insert(daily.capture());
        assertEquals(250, daily.getValue().getWaterMl(), "diet_daily 只是当天各笔之和的缓存");
    }

    @Test
    void addWater_negativeRemovesLastDrink() {
        WaterLog last = new WaterLog();
        last.setId(9L);
        last.setUserId(7L);
        last.setLogDate(LocalDate.of(2026, 10, 12));
        last.setMl(300);
        when(waterLogMapper.selectOne(any())).thenReturn(last);
        when(waterLogMapper.selectById(9L)).thenReturn(last);
        when(waterLogMapper.sumOfDay(eq(7L), any())).thenReturn(0);
        DietDaily dd = new DietDaily();
        dd.setWaterMl(300);
        when(dietDailyMapper.selectOne(any())).thenReturn(dd);
        WaterDTO dto = new WaterDTO();
        dto.setDate("2026-10-12");
        dto.setDelta(-250);

        int now = service().addWater(7L, dto);

        assertEquals(0, now);
        verify(waterLogMapper).deleteById(9L);
        assertEquals(0, dd.getWaterMl());
    }

    @Test
    void updateDrink_notOwn_throws() {
        WaterLog other = new WaterLog();
        other.setUserId(99L);
        when(waterLogMapper.selectById(9L)).thenReturn(other);
        WaterDrinkDTO dto = new WaterDrinkDTO();
        dto.setMl(100);

        assertThrows(BusinessException.class, () -> service().updateDrink(7L, 9L, dto));
    }

    @Test
    void addMeal_pastDayWithoutTimeUsesMealDefault() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-10-12");
        dto.setMeal("dinner");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("100"));

        MealItemVO item = service().addMeal(7L, dto);

        assertEquals("18:00", item.getTime(), "补记别的日子：晚餐默认 18:00");
    }

    @Test
    void addMeal_explicitTimeWins() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-10-12");
        dto.setMeal("lunch");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("100"));
        dto.setTime("13:05");

        assertEquals("13:05", service().addMeal(7L, dto).getTime());
    }

    @Test
    void day_sortsItemsByTimeAndDrinksWithoutTimeFirst() {
        MealLog late = log("lunch", "米饭", "100", "116", "2.6", "25.9", "0.3");
        late.setId(1L);
        late.setEatenAt(LocalTime.of(12, 40));
        MealLog early = log("lunch", "鸡蛋", "50", "139", "13.1", "2.4", "8.6");
        early.setId(2L);
        early.setEatenAt(LocalTime.of(12, 5));
        MealLog old = log("lunch", "汤", "200", "20", "1", "1", "1");
        old.setId(3L);
        when(mealLogMapper.selectList(any())).thenReturn(List.of(late, early, old));
        WaterLog migrated = new WaterLog();
        migrated.setId(1L);
        migrated.setMl(1000);
        WaterLog morning = new WaterLog();
        morning.setId(2L);
        morning.setMl(250);
        morning.setDrunkAt(LocalTime.of(9, 0));
        when(waterLogMapper.selectList(any())).thenReturn(List.of(morning, migrated));
        when(mealPhotoMapper.selectMealsWithPhoto(eq(7L), any())).thenReturn(List.of());
        when(settingService.get(7L)).thenReturn(new SettingVO());

        DayOverviewVO vo = service().day(7L, "2026-10-12");

        DayOverviewVO.MealGroup lunch = vo.getMeals().get(1);
        assertEquals(List.of("鸡蛋", "米饭", "汤"), lunch.getItems().stream().map(MealItemVO::getFoodName).toList(),
                "按时刻排，没有时刻的老条目在后");
        assertEquals("12:05", lunch.getFirstTime());
        assertEquals(1250, vo.getWater().getMl());
        assertNull(vo.getWater().getDrinks().get(0).getTime(), "旧数据迁来的那笔没有时刻，排在前面");
        assertEquals("09:00", vo.getWater().getDrinks().get(1).getTime());
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

    // ── 三餐一餐一个时刻、加餐按条、时刻可清除（ADR 0062）──

    @Test
    void addMeal_mainMealFollowsExistingMealTime() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        MealLog existing = log("lunch", "米饭", "100", "116", "2.6", "25.9", "0.3");
        existing.setEatenAt(java.time.LocalTime.of(12, 30));
        when(mealLogMapper.selectOne(any())).thenReturn(existing);
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-10-12");
        dto.setMeal("lunch");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("100"));
        dto.setTime("13:15");

        MealItemVO item = service().addMeal(7L, dto);

        assertEquals("12:30", item.getTime(), "午餐已有条目，新条目跟着整餐的时刻");
    }

    @Test
    void addMeal_mainMealWithClearedTimeStaysWithoutTime() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        when(mealLogMapper.selectOne(any())).thenReturn(log("dinner", "米饭", "100", "116", "2.6", "25.9", "0.3"));
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-10-12");
        dto.setMeal("dinner");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("100"));
        dto.setTime("19:00");

        assertNull(service().addMeal(7L, dto).getTime(), "整餐清掉了时刻，新条目也没有");
    }

    @Test
    void addMeal_snackUsesGivenTimeEvenWhenSnackHasEntries() {
        when(foodMapper.selectById(5L)).thenReturn(chicken());
        MealLogDTO dto = new MealLogDTO();
        dto.setDate("2026-10-12");
        dto.setMeal("snack");
        dto.setFoodId(5L);
        dto.setGrams(new BigDecimal("100"));
        dto.setTime("15:40");

        assertEquals("15:40", service().addMeal(7L, dto).getTime());
        verify(mealLogMapper, never()).selectOne(any());
    }

    @Test
    void retime_snackIdsMustAllBeSnacks() {
        MealLog a = log("snack", "苹果", "100", "52", "0.3", "14", "0.2");
        a.setId(1L);
        MealLog b = log("lunch", "米饭", "100", "116", "2.6", "25.9", "0.3");
        b.setId(2L);
        when(mealLogMapper.selectList(any())).thenReturn(List.of(a, b));
        com.example.fitapp.module.nutrition.dto.MealTimeDTO dto = new com.example.fitapp.module.nutrition.dto.MealTimeDTO();
        dto.setMeal("snack");
        dto.setIds(List.of(1L, 2L));
        dto.setTime("10:00");

        assertThrows(BusinessException.class, () -> service().retime(7L, dto));
    }

    @Test
    void retime_mainMealUpdatesWholeMealAndNullClears() {
        MealLog a = log("breakfast", "鸡蛋", "50", "144", "13", "1", "9");
        a.setId(1L);
        a.setEatenAt(java.time.LocalTime.of(8, 0));
        MealLog b = log("breakfast", "牛奶", "250", "54", "3", "5", "3");
        b.setId(2L);
        b.setEatenAt(java.time.LocalTime.of(8, 0));
        when(mealLogMapper.selectList(any())).thenReturn(List.of(a, b));
        com.example.fitapp.module.nutrition.dto.MealTimeDTO dto = new com.example.fitapp.module.nutrition.dto.MealTimeDTO();
        dto.setMeal("breakfast");
        dto.setDate("2026-10-12");

        List<MealItemVO> out = service().retime(7L, dto);

        assertEquals(2, out.size());
        assertTrue(out.stream().allMatch(i -> i.getTime() == null), "time 不给＝整餐清除");
        verify(mealLogMapper).update(isNull(), any());
    }

    @Test
    void updateDrink_clearTimeSetsNull() {
        com.example.fitapp.module.nutrition.entity.WaterLog w = new com.example.fitapp.module.nutrition.entity.WaterLog();
        w.setId(4L);
        w.setUserId(7L);
        w.setMl(250);
        w.setLogDate(java.time.LocalDate.of(2026, 10, 12));
        w.setDrunkAt(java.time.LocalTime.of(9, 30));
        when(waterLogMapper.selectById(4L)).thenReturn(w);
        com.example.fitapp.module.nutrition.dto.WaterDrinkDTO dto = new com.example.fitapp.module.nutrition.dto.WaterDrinkDTO();
        dto.setClearTime(true);

        assertNull(service().updateDrink(7L, 4L, dto).getTime());
        verify(waterLogMapper).update(isNull(), any());
    }
}
