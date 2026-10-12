package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
