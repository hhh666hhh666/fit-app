package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.vo.ImportResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 导入的 kJ 换算与常用份解析、同名跳过与必填失败的报告、编辑必须走显式 set（否则清不掉可选字段）。
 */
@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    /** lambdaUpdate().set(...) 立即解析列名，需要 TableInfo 缓存（同 ScheduleServiceTest）。 */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Food.class);
    }

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private FoodMapper foodMapper;

    private FoodService service() {
        return new FoodService(foodMapper, new ObjectMapper(), VALIDATOR);
    }

    private static FoodSaveDTO dto(String name) {
        FoodSaveDTO d = new FoodSaveDTO();
        d.setName(name);
        d.setKcal(new BigDecimal("118"));
        d.setProtein(new BigDecimal("24.6"));
        d.setFat(new BigDecimal("1.9"));
        d.setCarb(new BigDecimal("0.6"));
        d.setSodium(new BigDecimal("63"));
        return d;
    }

    private static Food ownFood(long id, long userId) {
        Food f = new Food();
        f.setId(id);
        f.setUserId(userId);
        f.setName("鸡胸肉（生）");
        return f;
    }

    @Test
    void create_duplicateName_throws() {
        when(foodMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service().create(7L, dto("鸡胸肉（生）")));
    }

    @Test
    void create_defaultsUnitToGram() {
        when(foodMapper.selectCount(any())).thenReturn(0L);

        service().create(7L, dto("  鸡胸肉（生）  "));

        ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
        verify(foodMapper).insert(captor.capture());
        assertEquals("鸡胸肉（生）", captor.getValue().getName());
        assertEquals("g", captor.getValue().getUnit());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(new BigDecimal("63"), captor.getValue().getSodium());
    }

    @Test
    void update_usesExplicitSetSoNullableFieldsCanBeCleared() {
        when(foodMapper.selectById(10L)).thenReturn(ownFood(10L, 7L));
        when(foodMapper.selectCount(any())).thenReturn(0L);

        service().update(7L, 10L, dto("鸡胸肉（生）"));

        // 关键断言：走 update(entity=null, wrapper) 显式 set 路径。
        // 若退化成 updateById，可选字段的 null（清空）会被全局 not_null 策略静默跳过（脏数据）。
        verify(foodMapper).update(ArgumentMatchers.<Food>isNull(), any());
        verify(foodMapper, never()).updateById(any(Food.class));
    }

    @Test
    void update_explicitSetCoversGi() {
        when(foodMapper.selectById(10L)).thenReturn(ownFood(10L, 7L));
        when(foodMapper.selectCount(any())).thenReturn(0L);
        FoodSaveDTO d = dto("鸡胸肉（生）");
        d.setGi(new BigDecimal("83.2"));

        service().update(7L, 10L, d);

        // 新增营养字段最容易漏的一步：实体与 DTO 都加了，却忘了在显式 set 链里带上——
        // 于是「新建能存、编辑存不进去」，且不报错。这里直接断言 set 子句里有 gi。
        var captor = ArgumentCaptor.forClass(
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(foodMapper).update(ArgumentMatchers.<Food>isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("gi"),
                "update 的显式 set 里没有 gi：" + captor.getValue().getSqlSet());
    }

    @Test
    void delete_notOwn_throws() {
        when(foodMapper.selectById(10L)).thenReturn(ownFood(10L, 99L));
        assertThrows(BusinessException.class, () -> service().delete(7L, 10L));
        verify(foodMapper, never()).deleteById(any(Long.class));
    }

    // ── 系统预置（user_id 为空）：只读 + 列表/轻量模式 ──

    private static Food presetFood(long id) {
        Food f = new Food();
        f.setId(id);
        f.setUserId(null);
        f.setName("苹果（代表值）");
        return f;
    }

    @Test
    void delete_presetFood_forbidden() {
        when(foodMapper.selectById(99L)).thenReturn(presetFood(99L));

        BusinessException ex = assertThrows(BusinessException.class, () -> service().delete(7L, 99L));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(foodMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void update_presetFood_forbidden() {
        when(foodMapper.selectById(99L)).thenReturn(presetFood(99L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().update(7L, 99L, dto("苹果（代表值）")));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(foodMapper, never()).update(ArgumentMatchers.<Food>isNull(), any());
    }

    @Test
    void get_presetFood_isReadableAndFlagged() {
        when(foodMapper.selectById(99L)).thenReturn(presetFood(99L));

        Food f = service().get(7L, 99L);

        assertEquals(Boolean.TRUE, f.getPreset());
        assertEquals("苹果（代表值）", f.getName());
    }

    @Test
    void count_sumsPresetAndMine() {
        // 调用顺序：先查预置（isNull），后查自有（eq）
        when(foodMapper.selectCount(any())).thenReturn(10L, 3L);

        Map<String, Long> m = service().count(7L, null);

        assertEquals(13L, m.get("total"));
        assertEquals(10L, m.get("preset"));
        assertEquals(3L, m.get("mine"));
    }

    @Test
    void listLite_stripsHeavyFieldsAndFlagsPreset() {
        Food full = ownFood(1L, 7L);
        full.setKcal(new BigDecimal("118"));
        full.setCalcium(new BigDecimal("100"));
        Food preset = presetFood(99L);
        when(foodMapper.selectList(any())).thenReturn(List.of(full, preset));

        List<Food> lite = service().listLite(7L, null, 10, 0);

        assertEquals(2, lite.size());
        assertEquals(new BigDecimal("118"), lite.get(0).getKcal());
        assertNull(lite.get(0).getCalcium(), "轻量模式不应带矿物质等重字段");
        assertEquals(Boolean.FALSE, lite.get(0).getPreset());
        assertEquals(Boolean.TRUE, lite.get(1).getPreset());
    }

    // ── 浏览次数（详情页查看时 +1；预置与自有均可浏览）──

    @Test
    void incrementView_presetFood_allowed() {
        when(foodMapper.selectById(99L)).thenReturn(presetFood(99L));

        service().incrementView(7L, 99L);

        verify(foodMapper).incrementViewCount(99L);
    }

    @Test
    void incrementView_ownFood_allowed() {
        when(foodMapper.selectById(10L)).thenReturn(ownFood(10L, 7L));

        service().incrementView(7L, 10L);

        verify(foodMapper).incrementViewCount(10L);
    }

    @Test
    void incrementView_notFound_throws() {
        when(foodMapper.selectById(5L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service().incrementView(7L, 5L));
        verify(foodMapper, never()).incrementViewCount(any(Long.class));
    }

    @Test
    void incrementView_otherUsersFood_throws() {
        when(foodMapper.selectById(10L)).thenReturn(ownFood(10L, 99L));

        assertThrows(BusinessException.class, () -> service().incrementView(7L, 10L));
        verify(foodMapper, never()).incrementViewCount(any(Long.class));
    }

    // ── 收藏（置顶标记）：预置与自有均可收藏 / 取消 ──

    @Test
    void setFavorite_presetFood_writesFavoriteColumn() {
        when(foodMapper.selectById(99L)).thenReturn(presetFood(99L));

        service().setFavorite(7L, 99L, true);

        var captor = ArgumentCaptor.forClass(
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(foodMapper).update(ArgumentMatchers.<Food>isNull(), captor.capture());
        assertTrue(String.join(",", captor.getValue().getSqlSet()).contains("favorite"),
                "收藏更新未写 favorite 列：" + captor.getValue().getSqlSet());
    }

    @Test
    void setFavorite_ownFood_canUnset() {
        when(foodMapper.selectById(10L)).thenReturn(ownFood(10L, 7L));

        service().setFavorite(7L, 10L, false);

        verify(foodMapper).update(ArgumentMatchers.<Food>isNull(), any());
    }

    @Test
    void setFavorite_notFound_throws() {
        when(foodMapper.selectById(5L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service().setFavorite(7L, 5L, true));
        verify(foodMapper, never()).update(ArgumentMatchers.<Food>isNull(), any());
    }

    @Test
    void setFavorite_otherUsersFood_throws() {
        when(foodMapper.selectById(10L)).thenReturn(ownFood(10L, 99L));

        assertThrows(BusinessException.class, () -> service().setFavorite(7L, 10L, true));
        verify(foodMapper, never()).update(ArgumentMatchers.<Food>isNull(), any());
    }

    // ── 列表排序：收藏置顶 → 浏览次数降序 → 名称 ──

    @Test
    void listOrder_favoriteFirst_thenViewCount() {
        when(foodMapper.selectList(any())).thenReturn(List.of());

        service().listLite(7L, null, 10, 0);

        var captor = ArgumentCaptor.forClass(
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(foodMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toUpperCase();
        int fav = sql.indexOf("FAVORITE");
        int view = sql.indexOf("VIEW_COUNT");
        assertTrue(fav >= 0 && view >= 0 && fav < view,
                "排序应为「收藏优先、再按浏览次数」：" + sql);
    }

    @Test
    void import_convertsKjAndParsesPortion() {
        when(foodMapper.selectList(any())).thenReturn(List.of());
        String json = """
                [
                  { "name": "鸡胸肉（生）", "kcal": 118, "protein": 24.6, "fat": 1.9, "carb": 0.6, "sodium": 63 },
                  { "name": "鸡蛋", "kj": 582, "protein": 13.1, "fat": 8.6, "carb": 2.4, "sodium": 131,
                    "portion": { "label": "1个", "grams": 50 } }
                ]
                """;

        ImportResultVO result = service().importText(7L, json);

        assertEquals(2, result.getImported());
        assertTrue(result.getSkipped().isEmpty());
        assertTrue(result.getFailed().isEmpty());

        ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
        verify(foodMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        Food egg = captor.getAllValues().get(1);
        // 582 kJ ÷ 4.184 = 139.10 kcal
        assertEquals(new BigDecimal("139.10"), egg.getKcal());
        assertEquals("1个", egg.getPortionLabel());
        assertEquals(new BigDecimal("50"), egg.getPortionGrams());
    }

    @Test
    void import_mapsGi() {
        when(foodMapper.selectList(any())).thenReturn(List.of());
        String json = """
                [ { "name": "白米饭", "kcal": 116, "protein": 2.6, "fat": 0.3, "carb": 25.9, "sodium": 2, "gi": 83.2 } ]
                """;

        ImportResultVO result = service().importText(7L, json);

        assertEquals(1, result.getImported());
        ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
        verify(foodMapper).insert(captor.capture());
        assertEquals(new BigDecimal("83.2"), captor.getValue().getGi());
    }

    @Test
    void import_negativeGi_fails() {
        when(foodMapper.selectList(any())).thenReturn(List.of());
        String json = """
                [ { "name": "负GI", "kcal": 1, "protein": 1, "fat": 1, "carb": 1, "sodium": 1, "gi": -1 } ]
                """;

        ImportResultVO result = service().importText(7L, json);

        assertEquals(0, result.getImported());
        assertEquals(1, result.getFailed().size());
        assertEquals("负GI", result.getFailed().get(0).getName());
        verify(foodMapper, never()).insert(any(Food.class));
    }

    @Test
    void import_skipsDuplicatesAndReportsFailures() {
        when(foodMapper.selectList(any())).thenReturn(List.of());
        String longName = "超".repeat(65);
        String json = """
                [
                  { "name": "鸡蛋", "kcal": 139, "protein": 13.1, "fat": 8.6, "carb": 2.4, "sodium": 131 },
                  { "name": "鸡蛋", "kcal": 100, "protein": 1, "fat": 1, "carb": 1, "sodium": 1 },
                  { "name": "缺热量", "protein": 1, "fat": 1, "carb": 1, "sodium": 1 },
                  { "name": "%s", "kcal": 1, "protein": 1, "fat": 1, "carb": 1, "sodium": 1 },
                  { "name": "怪单位", "unit": "kg", "kcal": 1, "protein": 1, "fat": 1, "carb": 1, "sodium": 1 }
                ]
                """.formatted(longName);

        ImportResultVO result = service().importText(7L, json);

        assertEquals(1, result.getImported());
        assertEquals(List.of("鸡蛋"), result.getSkipped());
        assertEquals(3, result.getFailed().size());
        // 失败项分别是：缺能量、名字过长、unit 非法
        assertTrue(result.getFailed().stream().anyMatch(f -> "缺热量".equals(f.getName())));
        assertTrue(result.getFailed().stream().anyMatch(f -> "怪单位".equals(f.getName())));
        assertTrue(result.getFailed().stream().anyMatch(f -> f.getName().length() == 65));
        verify(foodMapper).insert(any(Food.class));
    }

    @Test
    void import_skipsNamesAlreadyInLibrary() {
        Food existing = ownFood(1L, 7L);
        existing.setName("鸡蛋");
        when(foodMapper.selectList(any())).thenReturn(List.of(existing));
        String json = """
                [ { "name": "鸡蛋", "kcal": 139, "protein": 13.1, "fat": 8.6, "carb": 2.4, "sodium": 131 } ]
                """;

        ImportResultVO result = service().importText(7L, json);

        assertEquals(0, result.getImported());
        assertEquals(List.of("鸡蛋"), result.getSkipped());
        verify(foodMapper, never()).insert(any(Food.class));
    }

    @Test
    void import_badJson_throws() {
        assertThrows(BusinessException.class, () -> service().importText(7L, "{ not json"));
    }
}
