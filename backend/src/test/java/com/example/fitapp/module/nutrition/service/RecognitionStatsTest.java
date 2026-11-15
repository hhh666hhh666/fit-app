package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.module.nutrition.entity.FoodRecognition;
import com.example.fitapp.module.nutrition.vo.RecognitionStatsVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 识别用量统计的口径：范围按调用时刻划、只合计能计价的、失败照算、按模型／入口拆、逐日补 0。
 */
class RecognitionStatsTest {

    /** 2026-11-15 周日。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    private static FoodRecognition row(String model, String kind, LocalDateTime createdAt, int durationMs,
                                       int in, int cached, int out, boolean success) {
        FoodRecognition r = new FoodRecognition();
        r.setModel(model);
        r.setKind(kind);
        r.setCreatedAt(createdAt);
        r.setDurationMs(durationMs);
        r.setTokensIn(in);
        r.setCachedTokens(cached);
        r.setTokensOut(out);
        r.setSuccess(success);
        return r;
    }

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), actual.toPlainString());
    }

    private static List<FoodRecognition> sample() {
        return List.of(
                // 周一 10:00 高峰：0.006416 元（同 DeepSeekPricingTest）
                row("deepseek-flash", "meal", LocalDateTime.of(2026, 11, 9, 10, 0, 5), 5000, 2400, 400, 300, true),
                // 周一 12:30 空闲：0.003208 元；失败也算钱
                row("deepseek-flash", "label", LocalDateTime.of(2026, 11, 9, 12, 30), 1000, 2400, 400, 300, false),
                // 千问：不计价
                row("qwen3-vl-8b-instruct", null, LocalDateTime.of(2026, 11, 14, 20, 0), 3000, 1000, 0, 200, true),
                // 上个月：只有「全部」里有
                row("deepseek-flash", "meal", LocalDateTime.of(2026, 10, 30, 10, 0), 2000, 2400, 400, 300, true));
    }

    @Test
    void all_sumsPricedOnly_andCountsFailures() {
        RecognitionStatsVO s = RecognitionStats.compute(sample(), RecognitionStats.Range.ALL, TODAY);

        assertEquals(4, s.getRequests());
        assertEquals(3, s.getSuccesses());
        assertEquals(1, s.getFailures());
        assertEquals(2400 * 3 + 1000, s.getTokensIn());
        assertEquals(300 * 3 + 200, s.getTokensOut());
        assertEquals(s.getTokensIn() + s.getTokensOut(), s.getTotalTokens());
        assertEquals(3, s.getPricedRequests());
        assertEquals(1, s.getUnpricedRequests());
        assertEquals(1200, s.getUnpricedTokens());
        // 10-30 周五 10:00 高峰 0.006416 ＋ 两条 11-09
        eq("0.016040", s.getCost());
        eq("0.012832", s.getPeakCost());
        eq("0.003208", s.getOffPeakCost());
        eq("0.00534667", s.getAvgCost());
        assertEquals(1200.0 / 8200, s.getCacheHitRate(), 1e-9);
    }

    @Test
    void all_dailySeriesStartsAtFirstRecord_andFillsZeros() {
        RecognitionStatsVO s = RecognitionStats.compute(sample(), RecognitionStats.Range.ALL, TODAY);

        assertEquals(LocalDate.of(2026, 10, 30), s.getFrom());
        assertEquals(TODAY, s.getTo());
        assertEquals(17, s.getDaily().size());
        RecognitionStatsVO.Day nov9 = s.getDaily().get(10);
        assertEquals(LocalDate.of(2026, 11, 9), nov9.getDate());
        assertEquals(2, nov9.getRequests());
        eq("0.009624", nov9.getCost());
        assertEquals(0, s.getDaily().get(1).getRequests());
        eq("0", s.getDaily().get(16).getCost());
    }

    @Test
    void month_filtersByCallTime() {
        RecognitionStatsVO s = RecognitionStats.compute(sample(), RecognitionStats.Range.MONTH, TODAY);

        assertEquals(3, s.getRequests());
        assertEquals(LocalDate.of(2026, 11, 1), s.getFrom());
        assertEquals(15, s.getDaily().size());
        eq("0.009624", s.getCost());
    }

    @Test
    void week_isTodayPlusSixDays() {
        RecognitionStatsVO s = RecognitionStats.compute(sample(), RecognitionStats.Range.WEEK, TODAY);

        assertEquals(LocalDate.of(2026, 11, 9), s.getFrom());
        assertEquals(7, s.getDaily().size());
        assertEquals(3, s.getRequests());
    }

    @Test
    void callTimeDecidesTheDay_notRecordTime() {
        // 记录写于 11-09 00:00:03，耗时 5 秒 → 请求发出于 11-08 23:59:58，近 7 天（11-09 起）不含它
        FoodRecognition r = row("deepseek-flash", "meal", LocalDateTime.of(2026, 11, 9, 0, 0, 3), 5000, 100, 0, 10, true);
        RecognitionStatsVO s = RecognitionStats.compute(List.of(r), RecognitionStats.Range.WEEK, TODAY);

        assertEquals(0, s.getRequests());
        assertEquals(7, s.getDaily().size());
    }

    @Test
    void groupsByRawModelAndKind_sortedByRequests() {
        RecognitionStatsVO s = RecognitionStats.compute(sample(), RecognitionStats.Range.ALL, TODAY);

        assertEquals("deepseek-flash", s.getByModel().get(0).getKey());
        assertEquals(3, s.getByModel().get(0).getRequests());
        RecognitionStatsVO.Group qwen = s.getByModel().get(1);
        assertEquals(1, qwen.getUnpricedRequests());
        eq("0", qwen.getCost());

        assertEquals("meal", s.getByKind().get(0).getKey());
        assertEquals(2, s.getByKind().get(0).getRequests());
        assertTrue(s.getByKind().stream().anyMatch(g -> g.getKey() == null && g.getRequests() == 1));
    }

    @Test
    void empty_hasNoSeriesAndNullAverages() {
        RecognitionStatsVO all = RecognitionStats.compute(List.of(), RecognitionStats.Range.ALL, TODAY);
        assertEquals(0, all.getRequests());
        assertNull(all.getFrom());
        assertTrue(all.getDaily().isEmpty());
        assertNull(all.getAvgTokens());
        assertNull(all.getAvgCost());
        assertNull(all.getCacheHitRate());

        RecognitionStatsVO week = RecognitionStats.compute(List.of(), RecognitionStats.Range.WEEK, TODAY);
        assertEquals(7, week.getDaily().size());
    }

    @Test
    void uncoveredHolidayYear_isReported() {
        FoodRecognition r = row("deepseek-flash", "meal", LocalDateTime.of(2028, 3, 1, 10, 0), 0, 100, 0, 10, true);
        RecognitionStatsVO s = RecognitionStats.compute(List.of(r), RecognitionStats.Range.ALL, LocalDate.of(2028, 3, 2));

        assertEquals(List.of(2028), s.getUncoveredHolidayYears());
    }

    @Test
    void unknownRange_fallsBackToAll() {
        assertEquals(RecognitionStats.Range.ALL, RecognitionStats.Range.of("year"));
        assertEquals(RecognitionStats.Range.WEEK, RecognitionStats.Range.of("week"));
    }
}
