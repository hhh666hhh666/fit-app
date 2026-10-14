package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标签解析纯逻辑（不碰库、不碰 HTTP）。专测两个「静默错」——
 * kJ 当 kcal 直接用、NRV% 当绝对值混入——以及每份→每100 的换算。
 */
class LabelDraftParserTest {

    private static void assertBd(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + "，实际 " + actual);
    }

    @Test
    void kj_convertedToKcal() {
        String json = """
                {"name":"全麦面包","unit":"g","kj":1050,
                 "nutrients":{"protein":8.2,"fat":3.1,"carb":45.6,"sodium":210,"fiber":5.4},
                 "nrv":{"energy":13},"uncertain":[]}
                """;

        LabelDraftParser.Draft d = LabelDraftParser.parse(json);

        // 1050 kJ ÷ 4.184 = 250.96 kcal
        assertBd("250.96", d.dto().getKcal());
        assertEquals("全麦面包", d.dto().getName());
        assertBd("8.20", d.dto().getProtein());
        assertBd("5.40", d.dto().getFiber());
        assertTrue(d.uncertain().isEmpty());
    }

    @Test
    void nrvPercent_neverLeaksIntoNutrients() {
        // NRV% 若被当成数值混入，蛋白会变成 6 而不是 3.3——这里钉死
        String json = """
                {"name":"纯牛奶","unit":"ml","kj":274,
                 "nutrients":{"protein":3.3,"fat":3.6,"carb":4.8,"sodium":60},
                 "nrv":{"energy":3,"protein":6,"fat":6,"carb":2,"sodium":30},
                 "uncertain":[]}
                """;

        FoodSaveDTO dto = LabelDraftParser.parse(json).dto();

        assertBd("65.49", dto.getKcal()); // 274 / 4.184
        assertBd("3.30", dto.getProtein());
        assertBd("3.60", dto.getFat());
        assertBd("60.00", dto.getSodium());
    }

    @Test
    void kcalPrinted_passedThrough() {
        String json = """
                {"name":"进口巧克力","unit":"g","kcal":539,
                 "nutrients":{"protein":7.3,"fat":31.5,"carb":56.2,"sodium":80},"nrv":{},"uncertain":[]}
                """;

        LabelDraftParser.Draft d = LabelDraftParser.parse(json);

        assertBd("539.00", d.dto().getKcal());
    }

    @Test
    void perServing_scaledToPer100() {
        // 一份 50g：蛋白 6.2 → 每100 12.40；能量 1600kJ/份 → 3200kJ/100 → 764.68 kcal
        String json = """
                {"name":"酸奶","unit":"g","kj":1600,"serving_grams":50,
                 "nutrients":{"protein":6.2,"fat":2,"carb":5,"sodium":30},"nrv":{},"uncertain":[]}
                """;

        LabelDraftParser.Draft d = LabelDraftParser.parse(json);

        assertBd("764.82", d.dto().getKcal());
        assertBd("12.40", d.dto().getProtein());
        assertBd("4.00", d.dto().getFat());
        assertBd("60.00", d.dto().getSodium());
    }

    @Test
    void missingRequired_throwsWithNames() {
        String json = """
                {"name":"缺钠的面包","unit":"g","kj":1000,
                 "nutrients":{"protein":8,"fat":3,"carb":45},"nrv":{},"uncertain":[]}
                """;

        BusinessException ex = assertThrows(BusinessException.class, () -> LabelDraftParser.parse(json));

        assertTrue(ex.getMessage().contains("钠"), "应点明缺的是钠：" + ex.getMessage());
    }

    @Test
    void noLabel_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> LabelDraftParser.parse("{\"error\":\"no_label\"}"));
        assertTrue(ex.getMessage().contains("没找到营养成分表"), ex.getMessage());
    }

    @Test
    void fencedJson_stripped() {
        String fenced = """
                ```json
                {"name":"苏打饼干","unit":"g","kj":2000,
                 "nutrients":{"protein":8,"fat":20,"carb":60,"sodium":400},"nrv":{},"uncertain":[]}
                ```
                """;

        LabelDraftParser.Draft d = LabelDraftParser.parse(fenced);

        assertEquals("苏打饼干", d.dto().getName());
        assertBd("478.01", d.dto().getKcal()); // 2000 / 4.184 = 478.011…
    }

    @Test
    void uncertain_filteredToKnownKeys() {
        String json = """
                {"name":"?","unit":"g","kj":100,
                 "nutrients":{"protein":1,"fat":1,"carb":1,"sodium":1},
                 "nrv":{},"uncertain":["sodium","糖","name","protein"]}
                """;

        LabelDraftParser.Draft d = LabelDraftParser.parse(json);

        assertEquals(List.of("sodium", "name", "protein"), d.uncertain());
    }

    @Test
    void blankName_throws() {
        String json = """
                {"name":"  ","unit":"g","kj":100,
                 "nutrients":{"protein":1,"fat":1,"carb":1,"sodium":1},"nrv":{},"uncertain":[]}
                """;
        assertThrows(BusinessException.class, () -> LabelDraftParser.parse(json));
    }

    @Test
    void negativeNutrient_throws() {
        String json = """
                {"name":"脏数据","unit":"g","kj":100,
                 "nutrients":{"protein":-1,"fat":1,"carb":1,"sodium":1},"nrv":{},"uncertain":[]}
                """;
        assertThrows(BusinessException.class, () -> LabelDraftParser.parse(json));
    }

    @Test
    void invalidJson_throws() {
        assertThrows(BusinessException.class, () -> LabelDraftParser.parse("完全不是 JSON"));
    }

    @Test
    void unitMl_preserved() {
        String json = """
                {"name":"燕麦奶","unit":"ml","kj":200,
                 "nutrients":{"protein":1,"fat":1.5,"carb":6,"sodium":50},"nrv":{},"uncertain":[]}
                """;
        assertEquals("ml", LabelDraftParser.parse(json).dto().getUnit());
    }
}
