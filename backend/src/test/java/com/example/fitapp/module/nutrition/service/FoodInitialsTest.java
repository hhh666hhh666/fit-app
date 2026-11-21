package com.example.fitapp.module.nutrition.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodInitialsTest {

    @Test
    void plainName() {
        assertEquals("|zr|", FoodInitials.of("猪肉", null));
    }

    @Test
    void bracketAliasesAndAliasFieldAreSeparateSegments() {
        // 番（fān/pān）、茄（qié/jiā）、红（hóng/gōng）都是多音字，常用读法排在前面
        String tomato = FoodInitials.of("番茄［西红柿］", null);
        assertTrue(tomato.startsWith("|fq|") && tomato.contains("|xhs|"), tomato);
        String intestine = FoodInitials.of("猪大肠", List.of("肥肠"));
        assertTrue(intestine.startsWith("|zdc|") && intestine.contains("|fc|"), intestine);
    }

    @Test
    void polyphonesListEveryReading() {
        String s = FoodInitials.of("重庆小面", null);
        assertTrue(s.contains("|zqxm|") && s.contains("|cqxm|"), s);
    }

    @Test
    void lettersAndDigitsKeptOtherSymbolsDropped() {
        assertEquals("|adgn|", FoodInitials.of("AD钙奶", null));
        assertEquals("|zr2|", FoodInitials.of("猪肉（2）", null));
    }

    @Test
    void nothingToComputeIsNotNull() {
        assertEquals("|", FoodInitials.of("（）", null));
    }

    @Test
    void variantsCappedForLongPolyphoneNames() {
        // 每个「重」都有 z/c 两种读法，不限制就是 2^6 = 64 种
        List<String> v = FoodInitials.variants("重重重重重重");
        assertTrue(v.size() <= FoodInitials.MAX_VARIANTS, v.toString());
    }

    @Test
    void lengthCapped() {
        String longName = "重".repeat(64);
        List<String> aliases = java.util.Collections.nCopies(10, "长长长长长长长长长长长长长长长长");
        assertTrue(FoodInitials.of(longName, aliases).length() <= FoodInitials.MAX_LENGTH);
    }
}
