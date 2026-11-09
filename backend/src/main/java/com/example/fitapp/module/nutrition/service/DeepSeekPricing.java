package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.module.nutrition.vo.RecognitionPriceVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 调用价格估算（ADR 0054）：按调用时刻（北京时间）分高峰 / 空闲，再按 token 分项算钱。纯计算，无 Spring 依赖。
 *
 * <p><b>价目</b>：DeepSeek 官方定价页（api-docs.deepseek.com/zh-cn/quick_start/pricing，2026-11-09 读取），
 * 每百万 token、人民币、高峰价；空闲时段一律半价。价格调整时**往 {@link #TABLES} 里加一版、写上生效日**，别改旧的——
 * 老记录要按当时的价算。</p>
 *
 * <p><b>峰谷</b>：北京时间周一至周五（不含法定节假日）9:00–12:00、14:00–18:00 为高峰，其余（含周末、节假日全天）为空闲。
 * 「法定节假日」按国务院每年的放假安排里的放假日算；调休补班的周六周日仍是周末（官方只说周一至周五），算空闲。
 * 放假安排每年 11 月左右公布，**新的一年要往 {@link #HOLIDAYS} 里补**；没覆盖到的年份只按周末判断，并在结果里写明。</p>
 *
 * <p><b>口径</b>：输入 token 已含图片；输出 token 已含思考部分（OpenAI 兼容约定，completion_tokens 里包含 reasoning_tokens）。
 * 计费时刻取请求发出的时刻（记录时刻减去耗时）。</p>
 */
public final class DeepSeekPricing {

    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    /** 一版价目：每百万 token 的高峰价（元）。 */
    record Rates(String label, BigDecimal cacheHit, BigDecimal cacheMiss, BigDecimal output) {
    }

    /** 一版价目表（自 {@code from} 起生效）。 */
    record Table(LocalDate from, String source, Map<String, Rates> byModel) {
    }

    private static final Rates FLASH = new Rates("deepseek-flash（DeepSeek-V4.1-Flash-1017）",
            new BigDecimal("0.04"), new BigDecimal("2"), new BigDecimal("8"));
    private static final Rates V4_PRO = new Rates("deepseek-v4-pro（DeepSeek-V4-Pro-0813）",
            new BigDecimal("0.30"), new BigDecimal("9.0"), new BigDecimal("27.0"));

    /** 价目表，按生效日从早到晚。旧名 deepseek-v4-flash / deepseek-v4-flash-vision-exp 官方说按 Flash 计费。 */
    static final List<Table> TABLES = List.of(
            new Table(LocalDate.of(2026, 1, 1), "DeepSeek 官方定价页（2026-11-09 读取）", Map.of(
                    "deepseek-flash", FLASH,
                    "deepseek-v4-flash", FLASH,
                    "deepseek-v4-flash-vision-exp", FLASH,
                    "deepseek-v4-pro", V4_PRO)));

    /** 放假日 → 节日名（国务院办公厅 2026、2027 年部分节假日安排的通知）。 */
    static final Map<LocalDate, String> HOLIDAYS = new HashMap<>();

    /** {@link #HOLIDAYS} 覆盖到的年份。 */
    static final List<Integer> HOLIDAY_YEARS = List.of(2026, 2027);

    static {
        // 2026（国务院办公厅 2025-11-04 发布）
        range("元旦", 2026, 1, 1, 1, 3);
        range("春节", 2026, 2, 15, 2, 23);
        range("清明节", 2026, 4, 4, 4, 6);
        range("劳动节", 2026, 5, 1, 5, 5);
        range("端午节", 2026, 6, 19, 6, 21);
        range("中秋节", 2026, 9, 25, 9, 27);
        range("国庆节", 2026, 10, 1, 10, 7);
        // 2027（国务院办公厅 2026-11-05 发布，国办发明电〔2026〕10 号）
        range("元旦", 2027, 1, 1, 1, 3);
        range("春节", 2027, 2, 5, 2, 13);
        range("清明节", 2027, 4, 3, 4, 5);
        range("劳动节", 2027, 5, 1, 5, 5);
        range("端午节", 2027, 6, 9, 6, 9);
        range("中秋节", 2027, 9, 15, 9, 15);
        range("国庆节", 2027, 10, 1, 10, 7);
    }

    private static void range(String name, int year, int m1, int d1, int m2, int d2) {
        for (LocalDate d = LocalDate.of(year, m1, d1); !d.isAfter(LocalDate.of(year, m2, d2)); d = d.plusDays(1)) {
            HOLIDAYS.put(d, name);
        }
    }

    private static final LocalTime AM_START = LocalTime.of(9, 0);
    private static final LocalTime AM_END = LocalTime.of(12, 0);
    private static final LocalTime PM_START = LocalTime.of(14, 0);
    private static final LocalTime PM_END = LocalTime.of(18, 0);

    private DeepSeekPricing() {
    }

    /**
     * 估算一次调用的价格。
     *
     * @param model    模型名（不是 DeepSeek 的给「不计算」）
     * @param callTime 请求发出的时刻（北京时间）
     */
    public static RecognitionPriceVO estimate(String model, LocalDateTime callTime,
                                              int tokensIn, int cachedTokens, int tokensOut) {
        RecognitionPriceVO vo = new RecognitionPriceVO();
        String key = model == null ? "" : model.trim().toLowerCase();
        Table table = tableFor(callTime.toLocalDate());
        Rates rates = table == null ? null : table.byModel().get(key);
        if (rates == null) {
            vo.setSupported(false);
            vo.setNote(key.startsWith("deepseek")
                    ? "价目表里没有这个 DeepSeek 模型（" + model + "），没法算"
                    : "目前只计算 DeepSeek 的模型，这次用的是 " + model);
            return vo;
        }
        vo.setSupported(true);
        vo.setCurrency("CNY");
        vo.setModelLabel(rates.label());
        vo.setSource(table.source());
        vo.setCallTime(callTime);

        Period p = period(callTime);
        vo.setPeak(p.peak());
        vo.setPeriodReason(p.reason());
        vo.setHolidayCovered(HOLIDAY_YEARS.contains(callTime.getYear()));

        BigDecimal factor = p.peak() ? BigDecimal.ONE : HALF;
        int miss = Math.max(0, tokensIn - cachedTokens);
        List<RecognitionPriceVO.Item> items = new ArrayList<>();
        items.add(item("输入 · 缓存命中", cachedTokens, rates.cacheHit().multiply(factor)));
        items.add(item("输入 · 未命中缓存", miss, rates.cacheMiss().multiply(factor)));
        items.add(item("输出（含思考）", tokensOut, rates.output().multiply(factor)));
        vo.setItems(items);
        vo.setTotal(items.stream().map(RecognitionPriceVO.Item::getCost).reduce(BigDecimal.ZERO, BigDecimal::add));
        return vo;
    }

    private static RecognitionPriceVO.Item item(String label, int tokens, BigDecimal unitPerMillion) {
        RecognitionPriceVO.Item it = new RecognitionPriceVO.Item();
        it.setLabel(label);
        it.setTokens(tokens);
        it.setUnitPrice(unitPerMillion.stripTrailingZeros());
        it.setCost(unitPerMillion.multiply(BigDecimal.valueOf(tokens)).divide(MILLION, 8, RoundingMode.HALF_UP));
        return it;
    }

    private static Table tableFor(LocalDate day) {
        Table hit = null;
        for (Table t : TABLES) {
            if (!t.from().isAfter(day)) hit = t;
        }
        return hit;
    }

    record Period(boolean peak, String reason) {
    }

    /** 判断北京时间某一刻是高峰还是空闲，并给出人能看懂的理由。 */
    static Period period(LocalDateTime t) {
        LocalDate d = t.toLocalDate();
        String holiday = HOLIDAYS.get(d);
        if (holiday != null) {
            return new Period(false, "法定节假日（" + holiday + "）全天空闲");
        }
        DayOfWeek w = d.getDayOfWeek();
        if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) {
            return new Period(false, "周末全天空闲（调休补班也按周末算）");
        }
        LocalTime time = t.toLocalTime();
        if (!time.isBefore(AM_START) && time.isBefore(AM_END)) {
            return new Period(true, "工作日 09:00–12:00 高峰");
        }
        if (!time.isBefore(PM_START) && time.isBefore(PM_END)) {
            return new Period(true, "工作日 14:00–18:00 高峰");
        }
        return new Period(false, "工作日高峰时段以外（高峰为 09:00–12:00、14:00–18:00）");
    }
}
