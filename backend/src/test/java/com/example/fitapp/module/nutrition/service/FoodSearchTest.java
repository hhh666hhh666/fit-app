package com.example.fitapp.module.nutrition.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 搜索词 → LIKE 参数（ADR 0056）：分档用的几种写法、按字顺序的模糊写法、通配符转义。 */
class FoodSearchTest {

    @Test
    void blankKeyword_isNoSearch() {
        assertNull(FoodSearch.of(null));
        assertNull(FoodSearch.of("  \t "));
    }

    @Test
    void buildsTierPatternsAndCharOrderPattern() {
        FoodSearch t = FoodSearch.of(" 猪肠 ");

        assertEquals("猪肠", t.getKw());
        assertEquals("%|猪肠|%", t.getExactAlias());
        assertEquals("%猪肠%", t.getContains());
        assertEquals("%猪%肠%", t.getFuzzy());
        assertNull(t.getInitialsExact(), "有汉字就不走首字母");
        assertNull(t.getInitialsContains());
    }

    @Test
    void lettersOnlyAlsoMatchInitials() {
        FoodSearch t = FoodSearch.of(" ZR ");

        assertEquals("%|zr|%", t.getInitialsExact());
        assertEquals("%zr%", t.getInitialsContains());
    }

    @Test
    void mixedLettersAndDigitsDoNotUseInitials() {
        assertNull(FoodSearch.of("zr2").getInitialsContains());
    }

    @Test
    void fuzzyIgnoresInnerWhitespace() {
        assertEquals("%猪%肠%", FoodSearch.of("猪 肠").getFuzzy());
    }

    @Test
    void escapesLikeWildcardsWithBang() {
        FoodSearch t = FoodSearch.of("5%_!");

        assertEquals("%5!%!_!!%", t.getContains());
        assertEquals("%5%!%%!_%!!%", t.getFuzzy());
    }

    @Test
    void aliases_splitTrimDedupeAndDropOwnName() {
        FoodAliases.Parsed p = FoodAliases.parse(List.of("肥肠，大肠头 / 肥肠", "猪大肠", "  "), "猪大肠");

        assertNull(p.error());
        assertEquals(List.of("肥肠", "大肠头"), p.aliases());
        assertEquals("肥肠|大肠头", FoodAliases.join(p.aliases()));
        assertEquals(p.aliases(), FoodAliases.split("肥肠|大肠头"));
    }

    @Test
    void aliases_emptyIsNull() {
        assertNull(FoodAliases.parse(List.of(" ", "猪大肠"), "猪大肠").aliases());
        assertNull(FoodAliases.join(List.of()));
        assertNull(FoodAliases.split(""));
    }

    @Test
    void aliases_tooLong_reportsWhichOne() {
        String longOne = "一二三四五六七八九十一二三四五六七";
        FoodAliases.Parsed p = FoodAliases.parse(List.of(longOne), "x");

        assertEquals("别名「" + longOne + "」超过 16 个字", p.error());
    }
}
