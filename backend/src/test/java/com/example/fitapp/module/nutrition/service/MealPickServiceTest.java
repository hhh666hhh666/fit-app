package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.dto.PickHideDTO;
import com.example.fitapp.module.nutrition.entity.MealPickHidden;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
import com.example.fitapp.module.nutrition.mapper.MealPickHiddenMapper;
import com.example.fitapp.module.nutrition.vo.MealPickVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 记一餐列表：食物和临时条目按同一套规则排在一起（scope #48）。 */
@ExtendWith(MockitoExtension.class)
class MealPickServiceTest {

    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MealLog.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MealPickHidden.class);
    }

    @Mock
    private FoodService foodService;

    @Mock
    private MealLogMapper mealLogMapper;

    @Mock
    private MealPickHiddenMapper hiddenMapper;

    private MealPickService service() {
        lenient().when(foodService.toLite(any())).thenAnswer(inv -> inv.getArgument(0));
        return new MealPickService(foodService, mealLogMapper, hiddenMapper);
    }

    private static Food food(long id, String name, Long userId) {
        Food f = new Food();
        f.setId(id);
        f.setName(name);
        f.setUserId(userId);
        f.setInitials(FoodInitials.of(name, null));
        return f;
    }

    private static MealLog temp(String name, String kcal, String grams, LocalDate date, long id) {
        MealLog l = new MealLog();
        l.setId(id);
        l.setFoodName(name);
        l.setKcalPer100(new BigDecimal(kcal));
        l.setProteinPer100(BigDecimal.ONE);
        l.setFatPer100(BigDecimal.ONE);
        l.setCarbPer100(BigDecimal.ONE);
        l.setGrams(new BigDecimal(grams));
        l.setLogDate(date);
        l.setCreatedAt(date.atTime(12, 0).plusSeconds(id));
        return l;
    }

    private static List<String> names(List<MealPickVO> picks) {
        return picks.stream().map(p -> p.getFood() != null ? p.getFood().getName() : "临时:" + p.getTemp().getName()).toList();
    }

    @Test
    void tempEatenOftenRanksAboveNeverEatenPreset() {
        LocalDate today = LocalDate.now(FoodService.ZONE);
        when(foodService.searchForPicks(eq(7L), eq("红烧"), anyInt()))
                .thenReturn(List.of(food(1, "红烧鸡块", null)));
        when(mealLogMapper.lastUsedByFood(eq(7L), anyList())).thenReturn(List.of());
        when(mealLogMapper.selectList(any())).thenReturn(List.of(
                temp("红烧肉", "300", "150", today, 9),
                temp("红烧肉", "300", "120", today.minusDays(3), 8)));

        List<MealPickVO> picks = service().picks(7L, "红烧", 200);

        assertEquals(List.of("临时:红烧肉", "红烧鸡块"), names(picks));
        MealPickVO.Temp t = picks.get(0).getTemp();
        assertEquals(2, t.getCount(), "名字和营养一样的合成一种");
        assertEquals(new BigDecimal("150"), t.getGrams(), "份量取最近一次的");
    }

    @Test
    void exactMatchStaysFirstAndDifferentNutritionIsAnotherTemp() {
        LocalDate today = LocalDate.now(FoodService.ZONE);
        when(foodService.searchForPicks(eq(7L), eq("米饭"), anyInt()))
                .thenReturn(List.of(food(1, "米饭", null)));
        when(mealLogMapper.lastUsedByFood(eq(7L), anyList())).thenReturn(List.of());
        when(mealLogMapper.selectList(any())).thenReturn(List.of(
                temp("糙米饭", "120", "200", today, 9),
                temp("糙米饭", "130", "200", today, 8),
                temp("鸡蛋羹", "80", "100", today, 7)));

        List<MealPickVO> picks = service().picks(7L, "米饭", 200);

        assertEquals(List.of("米饭", "临时:糙米饭", "临时:糙米饭"), names(picks));
    }

    @Test
    void initialsMatchTemps() {
        when(foodService.searchForPicks(eq(7L), eq("hsr"), anyInt())).thenReturn(List.of());
        when(mealLogMapper.selectList(any())).thenReturn(List.of(
                temp("红烧肉", "300", "150", LocalDate.now(FoodService.ZONE), 9)));

        assertEquals(List.of("临时:红烧肉"), names(service().picks(7L, "hsr", 200)));
    }

    @Test
    void lastUsedOrdersFoodsAndTempsTogether() {
        LocalDate today = LocalDate.now(FoodService.ZONE);
        when(foodService.searchForPicks(eq(7L), eq(null), anyInt()))
                .thenReturn(List.of(food(1, "鸡胸肉", 7L), food(2, "苹果", null), food(3, "香蕉", null)));
        // 鸡胸肉三天前记过，苹果昨天记过（ADR 0064：只看最后一次，不看次数）；香蕉没记过
        when(mealLogMapper.lastUsedByFood(eq(7L), anyList())).thenReturn(List.of(
                Map.of("fid", 1L, "last", today.minusDays(3).atTime(9, 0)),
                Map.of("fid", 2L, "last", java.sql.Timestamp.valueOf(today.minusDays(1).atTime(9, 0)))));
        when(mealLogMapper.selectList(any())).thenReturn(List.of(
                temp("红烧肉", "300", "150", today.minusDays(2), 9)));

        assertEquals(List.of("苹果", "临时:红烧肉", "鸡胸肉", "香蕉"), names(service().picks(7L, null, 200)));
    }

    private static MealPickHidden hidden(String key, java.time.LocalDateTime at) {
        MealPickHidden h = new MealPickHidden();
        h.setPickKey(key);
        h.setHiddenAt(at);
        return h;
    }

    @Test
    void hiddenTempDisappearsUntilEatenAgain() {
        LocalDate today = LocalDate.now(FoodService.ZONE);
        MealLog old = temp("红烧肉", "300", "150", today.minusDays(2), 9);
        MealLog other = temp("鸡蛋羹", "80", "100", today.minusDays(1), 8);
        when(foodService.searchForPicks(eq(7L), eq(null), anyInt())).thenReturn(List.of());
        when(mealLogMapper.selectList(any())).thenReturn(List.of(old, other));
        String key = MealPickService.keyOf("红烧肉", new BigDecimal("300"), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, null);
        // 移除在最后一次记之后：不出现
        when(hiddenMapper.selectList(any())).thenReturn(List.of(hidden(key, today.atTime(8, 0))));
        assertEquals(List.of("临时:鸡蛋羹"), names(service().picks(7L, null, 200)));

        // 移除之后又记了一条：重新出现
        MealLog again = temp("红烧肉", "300", "150", today, 10);
        again.setCreatedAt(today.atTime(9, 0));
        when(mealLogMapper.selectList(any())).thenReturn(List.of(again, old, other));
        assertEquals(List.of("临时:红烧肉", "临时:鸡蛋羹"), names(service().picks(7L, null, 200)));
    }

    @Test
    void hideStoresKeyMatchingListGrouping() {
        PickHideDTO dto = new PickHideDTO();
        dto.setName(" 红烧肉 ");
        dto.setKcal(new BigDecimal("300.00"));
        dto.setProtein(new BigDecimal("1.0"));
        dto.setFat(BigDecimal.ONE);
        dto.setCarb(BigDecimal.ONE);
        service().hide(7L, dto);
        verify(hiddenMapper).insert(org.mockito.ArgumentMatchers.<MealPickHidden>argThat(h ->
                h.getUserId() == 7L && h.getPickKey().equals("红烧肉|300|1|1|1|")));
    }
}
