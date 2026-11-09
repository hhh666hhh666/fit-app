package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.module.nutrition.vo.RecognitionPriceVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeepSeek 价格估算（ADR 0054）：峰谷判断（工作日两段高峰、周末、法定节假日、调休补班）与分项算钱。
 */
class DeepSeekPricingTest {

    private static LocalDateTime at(int y, int m, int d, int hh, int mm) {
        return LocalDateTime.of(y, m, d, hh, mm);
    }

    @Test
    void weekdayMorning_isPeak_andPricesAtFullRate() {
        // 2026-11-09 周一 10:00：高峰，flash 全价 0.04 / 2 / 8 元每百万
        RecognitionPriceVO p = DeepSeekPricing.estimate("deepseek-flash", at(2026, 11, 9, 10, 0), 2400, 400, 300);

        assertTrue(p.isSupported());
        assertTrue(p.isPeak());
        assertEquals(0, new BigDecimal("0.04").compareTo(p.getItems().get(0).getUnitPrice()));
        // (400×0.04 + 2000×2 + 300×8) ÷ 1e6 = (16 + 4000 + 2400) ÷ 1e6 = 0.006416 元
        assertEquals(0, new BigDecimal("0.006416").compareTo(p.getTotal()), p.getTotal().toPlainString());
        assertEquals(2000, p.getItems().get(1).getTokens());
    }

    @Test
    void offPeak_isHalfPrice() {
        // 同一天 12:30 午间：空闲，半价
        RecognitionPriceVO p = DeepSeekPricing.estimate("deepseek-flash", at(2026, 11, 9, 12, 30), 2400, 400, 300);

        assertFalse(p.isPeak());
        assertEquals(0, new BigDecimal("1").compareTo(p.getItems().get(1).getUnitPrice()));
        assertEquals(0, new BigDecimal("0.003208").compareTo(p.getTotal()), p.getTotal().toPlainString());
    }

    @Test
    void peakBoundaries_startInclusive_endExclusive() {
        assertTrue(DeepSeekPricing.period(at(2026, 11, 9, 9, 0)).peak());
        assertFalse(DeepSeekPricing.period(at(2026, 11, 9, 8, 59)).peak());
        assertFalse(DeepSeekPricing.period(at(2026, 11, 9, 12, 0)).peak());
        assertTrue(DeepSeekPricing.period(at(2026, 11, 9, 14, 0)).peak());
        assertTrue(DeepSeekPricing.period(at(2026, 11, 9, 17, 59)).peak());
        assertFalse(DeepSeekPricing.period(at(2026, 11, 9, 18, 0)).peak());
        assertFalse(DeepSeekPricing.period(at(2026, 11, 9, 20, 10)).peak());
    }

    @Test
    void weekend_isOffPeakAllDay() {
        // 2026-11-07 周六 10:00
        DeepSeekPricing.Period p = DeepSeekPricing.period(at(2026, 11, 7, 10, 0));
        assertFalse(p.peak());
        assertTrue(p.reason().contains("周末"));
    }

    @Test
    void holidayOnWeekday_isOffPeak() {
        // 2026-10-05 周一，国庆假期
        DeepSeekPricing.Period p = DeepSeekPricing.period(at(2026, 10, 5, 10, 0));
        assertFalse(p.peak());
        assertTrue(p.reason().contains("国庆节"));
    }

    @Test
    void makeUpWorkdayOnSaturday_staysOffPeak() {
        // 2026-10-10 周六调休补班：官方只说周一至周五，仍按周末算空闲
        assertFalse(DeepSeekPricing.period(at(2026, 10, 10, 10, 0)).peak());
    }

    @Test
    void legacyFlashName_billedAsFlash() {
        RecognitionPriceVO p = DeepSeekPricing.estimate("deepseek-v4-flash-vision-exp", at(2026, 11, 9, 10, 0), 100, 0, 0);
        assertTrue(p.isSupported());
        assertTrue(p.getModelLabel().startsWith("deepseek-flash"));
    }

    @Test
    void nonDeepSeekModel_notSupported() {
        RecognitionPriceVO p = DeepSeekPricing.estimate("glm-4.6v", at(2026, 11, 9, 10, 0), 100, 0, 10);
        assertFalse(p.isSupported());
        assertTrue(p.getNote().contains("只计算 DeepSeek"));
    }

    @Test
    void yearWithoutHolidayTable_flagged() {
        RecognitionPriceVO p = DeepSeekPricing.estimate("deepseek-flash", at(2028, 3, 1, 10, 0), 100, 0, 10);
        assertFalse(p.isHolidayCovered());
    }
}
