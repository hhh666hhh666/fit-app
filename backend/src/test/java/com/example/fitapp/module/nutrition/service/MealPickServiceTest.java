package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
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
import static org.mockito.Mockito.when;

/** 记一餐列表：食物和临时条目按同一套规则排在一起（scope #48）。 */
@ExtendWith(MockitoExtension.class)
class MealPickServiceTest {

    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MealLog.class);
    }

    @Mock
    private FoodService foodService;

    @Mock
    private MealLogMapper mealLogMapper;

    private MealPickService service() {
        lenient().when(foodService.toLite(any())).thenAnswer(inv -> inv.getArgument(0));
        return new MealPickService(foodService, mealLogMapper);
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
        when(mealLogMapper.countRecentByFood(eq(7L), any(), anyList())).thenReturn(List.of());
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
        when(mealLogMapper.countRecentByFood(eq(7L), any(), anyList())).thenReturn(List.of());
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
    void recordCountOrdersFoodsAndTempsTogether() {
        LocalDate today = LocalDate.now(FoodService.ZONE);
        when(foodService.searchForPicks(eq(7L), eq(null), anyInt()))
                .thenReturn(List.of(food(1, "鸡胸肉", 7L), food(2, "苹果", null)));
        when(mealLogMapper.countRecentByFood(eq(7L), any(), anyList()))
                .thenReturn(List.of(Map.of("fid", 1L, "cnt", 5L), Map.of("fid", 2L, "cnt", 1L)));
        when(mealLogMapper.selectList(any())).thenReturn(List.of(
                temp("红烧肉", "300", "150", today, 9),
                temp("红烧肉", "300", "150", today, 8)));

        assertEquals(List.of("鸡胸肉", "临时:红烧肉", "苹果"), names(service().picks(7L, null, 200)));
    }
}
