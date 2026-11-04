package com.example.fitapp.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.admin.vo.ImportPreviewVO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.service.FoodService;
import com.example.fitapp.module.preset.service.PresetHiddenService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 导入把 id 不存在的条目当成新增、把钠当成预置必填、预检偷偷写库；重新上架没清掉用户的隐藏。
 */
@ExtendWith(MockitoExtension.class)
class AdminFoodServiceTest {

    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Food.class);
    }

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private FoodMapper foodMapper;

    @Mock
    private FoodService foodService;

    @Mock
    private PresetHiddenService presetHiddenService;

    private AdminFoodService service() {
        return new AdminFoodService(foodMapper, foodService, presetHiddenService, new ObjectMapper(), VALIDATOR);
    }

    private static final String TEXT = """
            [
              {"id": 5, "name": "燕麦", "category": "谷类", "kcal": 377, "protein": 15, "fat": 6.7, "carb": 66.9},
              {"name": "新预置", "category": "谷类", "kj": 418.4, "protein": 1, "fat": 1, "carb": 1},
              {"id": 999, "name": "不存在", "category": "谷类", "kcal": 1, "protein": 1, "fat": 1, "carb": 1},
              {"id": 5, "name": "重复 id", "category": "谷类", "kcal": 1, "protein": 1, "fat": 1, "carb": 1},
              {"name": "没分类", "kcal": 1, "protein": 1, "fat": 1, "carb": 1},
              {"name": "缺能量", "category": "谷类", "protein": 1, "fat": 1, "carb": 1}
            ]
            """;

    @Test
    void preview_classifiesWithoutWriting() {
        when(foodMapper.selectObjs(any())).thenReturn(List.of(5L, 6L));

        ImportPreviewVO vo = service().previewImport(TEXT);

        assertEquals(1, vo.create());
        assertEquals(1, vo.update());
        assertEquals(List.of(3, 4, 5, 6), vo.invalid().stream().map(ImportPreviewVO.Invalid::index).toList());
        // 预检只读：一行都不能写
        verify(foodMapper, never()).insert(any(Food.class));
        verify(foodMapper, never()).update(any(), any());
    }

    @Test
    void import_insertsNewAsPresetAndUpdatesById() {
        when(foodMapper.selectObjs(any())).thenReturn(List.of(5L));

        service().importText(TEXT);

        ArgumentCaptor<Food> inserted = ArgumentCaptor.forClass(Food.class);
        verify(foodMapper, times(1)).insert(inserted.capture());
        Food f = inserted.getValue();
        assertEquals(null, f.getUserId(), "新增的必须是预置（user_id 为空）");
        assertEquals("谷类", f.getCategory());
        assertEquals(0, f.getKcal().compareTo(new java.math.BigDecimal("100")), "kJ 要换算成 kcal");
        assertEquals(null, f.getSodium(), "预置的钠可以不填");

        ArgumentCaptor<LambdaUpdateWrapper> updated = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(foodMapper, times(1)).update(ArgumentMatchers.<Food>isNull(), updated.capture());
        String sets = updated.getValue().getSqlSet();
        assertTrue(sets.contains("category") && !sets.contains("favorite") && !sets.contains("archived"), sets);
    }

    @Test
    void unarchive_clearsEveryUsersHiding() {
        when(foodMapper.selectCount(any())).thenReturn(2L);

        service().unarchive(List.of(5L, 6L, 5L));

        verify(presetHiddenService).clearAll(eq("food"), eq(List.of(5L, 6L)));
    }

    @Test
    void archive_rejectsBatchWithNonPreset() {
        when(foodMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service().archive(List.of(5L, 77L)));
        verify(foodMapper, never()).update(any(), any());
    }

    @Test
    void get_userFood_notFound() {
        Food own = new Food();
        own.setId(9L);
        own.setUserId(3L);
        when(foodMapper.selectById(9L)).thenReturn(own);

        assertThrows(BusinessException.class, () -> service().get(9L));
    }
}
