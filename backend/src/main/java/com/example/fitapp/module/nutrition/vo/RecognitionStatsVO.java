package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 识别用量统计（「用量统计」页，ADR 0054 后续注记）：一个时间范围内的合计、平均、按模型／按入口拆分与逐日序列。
 *
 * <p>金额只合计价目表能算的记录（目前只有 DeepSeek），其余记录计入 {@link #unpricedRequests} / {@link #unpricedTokens}；
 * 失败的调用照算 token 与钱（与流水口径一致）。时间一律按请求发出的时刻（记录时刻减耗时）、北京时间划分。</p>
 */
@Data
public class RecognitionStatsVO {

    /** 范围：all / month / week。 */
    private String range;

    /** 范围起止日（含两端）；「全部」且没有记录时为 null。 */
    private LocalDate from;
    private LocalDate to;

    /** 请求次数（含失败）、成功、失败。 */
    private long requests;
    private long successes;
    private long failures;

    /** 输入 token（含缓存命中部分）、其中缓存命中、输出 token、其中思考、合计（输入＋输出）。 */
    private long tokensIn;
    private long cachedTokens;
    private long tokensOut;
    private long reasoningTokens;
    private long totalTokens;

    /** 缓存命中率＝缓存命中 ÷ 输入；没有输入时为 null。 */
    private Double cacheHitRate;

    /** 估算金额（元）：能计价记录的合计，及其中高峰、空闲各多少。 */
    private BigDecimal cost = BigDecimal.ZERO;
    private BigDecimal peakCost = BigDecimal.ZERO;
    private BigDecimal offPeakCost = BigDecimal.ZERO;

    /** 能计价的次数；算不了价的次数与它们的 token（输入＋输出）。 */
    private long pricedRequests;
    private long unpricedRequests;
    private long unpricedTokens;

    /** 平均每次：token（全部记录）、金额（只按能计价的记录平均）、耗时 ms（有耗时的记录）。没有可平均的为 null。 */
    private Double avgTokens;
    private BigDecimal avgCost;
    private Double avgDurationMs;

    /** 范围内能计价的记录落在了哪些放假表没覆盖的年份（这些年份节假日只按周末判断）。 */
    private List<Integer> uncoveredHolidayYears = new ArrayList<>();

    /** 按模型（原样模型名，不合并别名）拆，次数多的在前。 */
    private List<Group> byModel = new ArrayList<>();

    /** 按入口（label / meal；老记录为 null）拆，次数多的在前。 */
    private List<Group> byKind = new ArrayList<>();

    /** 逐日序列：从 from 到 to，每天一点，没记录的天为 0。 */
    private List<Day> daily = new ArrayList<>();

    @Data
    public static class Group {
        /** 模型名或入口；null＝老记录没记。 */
        private String key;
        private long requests;
        /** token 合计（输入＋输出）。 */
        private long tokens;
        /** 能计价部分的金额（元）。 */
        private BigDecimal cost = BigDecimal.ZERO;
        /** 其中算不了价的次数（等于 requests 时整组「未计价」）。 */
        private long unpricedRequests;
    }

    @Data
    public static class Day {
        private LocalDate date;
        private long requests;
        private long tokens;
        /** 能计价部分的金额（元）。 */
        private BigDecimal cost = BigDecimal.ZERO;
    }
}
