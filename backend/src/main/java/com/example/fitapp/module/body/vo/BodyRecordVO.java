package com.example.fitapp.module.body.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记录明细行（体重与围度同一条时间线）：带四围，供列表展示与就地编辑。
 * diff＝与时间上相邻上一条体重之差（首条为 null；本条无体重则为 null）。
 */
@Data
public class BodyRecordVO {

    private Long id;

    private LocalDateTime date;

    private BigDecimal weight;

    private BigDecimal chest;

    private BigDecimal waist;

    private BigDecimal hip;

    private BigDecimal arm;

    private String note;

    private BigDecimal diff;
}
