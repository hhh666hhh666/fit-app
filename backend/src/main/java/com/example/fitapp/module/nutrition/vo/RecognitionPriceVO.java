package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次识别调用的价格估算（ADR 0054）：目前只算 DeepSeek 的模型，按调用时刻分高峰 / 空闲。
 * 是按官方价目的估算，以服务商账单为准。
 */
@Data
public class RecognitionPriceVO {

    /** 能不能算：不是 DeepSeek 的模型、或价目表里没有这个模型时为 false，原因见 {@link #note}。 */
    private boolean supported;

    /** 不能算时的原因。 */
    private String note;

    /** 币种（CNY）。 */
    private String currency;

    /** 价目对应的模型（含官方版本号）。 */
    private String modelLabel;

    /** 价目出处。 */
    private String source;

    /** 用来判断峰谷的时刻：请求发出的时刻（北京时间）。 */
    private LocalDateTime callTime;

    /** 是否高峰时段。 */
    private boolean peak;

    /** 为什么是高峰 / 空闲。 */
    private String periodReason;

    /** 这一年的放假安排在不在表里；不在时只按周末判断节假日。 */
    private boolean holidayCovered;

    /** 分项：缓存命中输入、未命中输入、输出。 */
    private List<Item> items = new ArrayList<>();

    /** 合计（元）。 */
    private BigDecimal total;

    @Data
    public static class Item {
        private String label;
        private int tokens;
        /** 这一时段的单价：元 / 百万 token。 */
        private BigDecimal unitPrice;
        /** 这一项的钱（元）。 */
        private BigDecimal cost;
    }
}
