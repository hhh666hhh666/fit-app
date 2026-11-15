package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.module.nutrition.entity.FoodRecognition;
import com.example.fitapp.module.nutrition.vo.RecognitionPriceVO;
import com.example.fitapp.module.nutrition.vo.RecognitionStatsVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 识别用量统计的口径（「用量统计」页）：纯计算，无 Spring 依赖，便于单测。
 *
 * <p>峰谷要按每条的调用时刻判断，金额没法在 SQL 里求和，只能逐条调 {@link DeepSeekPricing#estimate} 再累加。
 * 时刻一律取「请求发出的时刻」＝记录时刻减耗时（与详情页的计价时刻同一个），北京时间。</p>
 */
public final class RecognitionStats {

    /** 范围：全部 / 本月（当月 1 号起）/ 近 7 天（今天加前 6 个自然日）。 */
    public enum Range {
        ALL("all"), MONTH("month"), WEEK("week");

        final String code;

        Range(String code) {
            this.code = code;
        }

        public static Range of(String s) {
            if (s == null) return ALL;
            for (Range r : values()) {
                if (r.code.equalsIgnoreCase(s.trim())) return r;
            }
            return ALL;
        }

        /** 起始日（含）；「全部」为 null。 */
        public LocalDate start(LocalDate today) {
            return switch (this) {
                case ALL -> null;
                case MONTH -> today.withDayOfMonth(1);
                case WEEK -> today.minusDays(6);
            };
        }
    }

    private RecognitionStats() {
    }

    /** 请求发出的时刻：记录是调用结束后写的，往回减掉耗时。 */
    public static LocalDateTime callTime(FoodRecognition r) {
        if (r.getCreatedAt() == null) return null;
        return r.getCreatedAt().minusNanos(1_000_000L * nz(r.getDurationMs()));
    }

    /**
     * 统计一个范围。
     *
     * @param rows  本人的记录（可多给，范围外的会按调用时刻滤掉）
     * @param today 今天（北京时间）
     */
    public static RecognitionStatsVO compute(List<FoodRecognition> rows, Range range, LocalDate today) {
        RecognitionStatsVO vo = new RecognitionStatsVO();
        vo.setRange(range.code);
        LocalDate start = range.start(today);

        Map<String, RecognitionStatsVO.Group> models = new LinkedHashMap<>();
        Map<String, RecognitionStatsVO.Group> kinds = new LinkedHashMap<>();
        Map<LocalDate, RecognitionStatsVO.Day> days = new HashMap<>();
        TreeSet<Integer> uncovered = new TreeSet<>();
        LocalDate first = null;
        long durationSum = 0;
        long durationCount = 0;

        for (FoodRecognition r : rows) {
            LocalDateTime t = callTime(r);
            if (t == null) continue;
            LocalDate d = t.toLocalDate();
            if (start != null && d.isBefore(start)) continue;
            if (d.isAfter(today)) continue;
            if (first == null || d.isBefore(first)) first = d;

            int in = nz(r.getTokensIn());
            int out = nz(r.getTokensOut());
            int cached = nz(r.getCachedTokens());
            long tokens = (long) in + out;

            vo.setRequests(vo.getRequests() + 1);
            if (Boolean.TRUE.equals(r.getSuccess())) vo.setSuccesses(vo.getSuccesses() + 1);
            else vo.setFailures(vo.getFailures() + 1);
            vo.setTokensIn(vo.getTokensIn() + in);
            vo.setCachedTokens(vo.getCachedTokens() + cached);
            vo.setTokensOut(vo.getTokensOut() + out);
            vo.setReasoningTokens(vo.getReasoningTokens() + nz(r.getReasoningTokens()));
            if (r.getDurationMs() != null) {
                durationSum += r.getDurationMs();
                durationCount++;
            }

            RecognitionPriceVO price = DeepSeekPricing.estimate(r.getModel(), t, in, cached, out);
            BigDecimal cost = price.isSupported() ? price.getTotal() : BigDecimal.ZERO;
            if (price.isSupported()) {
                vo.setPricedRequests(vo.getPricedRequests() + 1);
                vo.setCost(vo.getCost().add(cost));
                if (price.isPeak()) vo.setPeakCost(vo.getPeakCost().add(cost));
                else vo.setOffPeakCost(vo.getOffPeakCost().add(cost));
                if (!price.isHolidayCovered()) uncovered.add(t.getYear());
            } else {
                vo.setUnpricedRequests(vo.getUnpricedRequests() + 1);
                vo.setUnpricedTokens(vo.getUnpricedTokens() + tokens);
            }

            add(models.computeIfAbsent(r.getModel(), RecognitionStats::group), tokens, cost, price.isSupported());
            add(kinds.computeIfAbsent(r.getKind(), RecognitionStats::group), tokens, cost, price.isSupported());

            RecognitionStatsVO.Day day = days.computeIfAbsent(d, k -> {
                RecognitionStatsVO.Day x = new RecognitionStatsVO.Day();
                x.setDate(k);
                return x;
            });
            day.setRequests(day.getRequests() + 1);
            day.setTokens(day.getTokens() + tokens);
            day.setCost(day.getCost().add(cost));
        }

        vo.setTotalTokens(vo.getTokensIn() + vo.getTokensOut());
        if (vo.getTokensIn() > 0) vo.setCacheHitRate((double) vo.getCachedTokens() / vo.getTokensIn());
        if (vo.getRequests() > 0) vo.setAvgTokens((double) vo.getTotalTokens() / vo.getRequests());
        if (vo.getPricedRequests() > 0) {
            vo.setAvgCost(vo.getCost().divide(BigDecimal.valueOf(vo.getPricedRequests()), 8, RoundingMode.HALF_UP));
        }
        if (durationCount > 0) vo.setAvgDurationMs((double) durationSum / durationCount);
        vo.setUncoveredHolidayYears(new ArrayList<>(uncovered));

        Comparator<RecognitionStatsVO.Group> byRequests =
                Comparator.comparingLong(RecognitionStatsVO.Group::getRequests).reversed();
        vo.setByModel(models.values().stream().sorted(byRequests).toList());
        vo.setByKind(kinds.values().stream().sorted(byRequests).toList());

        // 逐日：从范围起点（「全部」取第一条那天）到今天，没记录的天补 0
        LocalDate from = start != null ? start : first;
        vo.setFrom(from);
        vo.setTo(from == null ? null : today);
        List<RecognitionStatsVO.Day> series = new ArrayList<>();
        if (from != null) {
            for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
                RecognitionStatsVO.Day day = days.get(d);
                if (day == null) {
                    day = new RecognitionStatsVO.Day();
                    day.setDate(d);
                }
                series.add(day);
            }
        }
        vo.setDaily(series);
        return vo;
    }

    private static RecognitionStatsVO.Group group(String key) {
        RecognitionStatsVO.Group g = new RecognitionStatsVO.Group();
        g.setKey(key);
        return g;
    }

    private static void add(RecognitionStatsVO.Group g, long tokens, BigDecimal cost, boolean priced) {
        g.setRequests(g.getRequests() + 1);
        g.setTokens(g.getTokens() + tokens);
        g.setCost(g.getCost().add(cost));
        if (!priced) g.setUnpricedRequests(g.getUnpricedRequests() + 1);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
