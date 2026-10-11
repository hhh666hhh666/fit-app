package com.example.fitapp.module.body.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 体重明细行：diff＝与时间上相邻上一条体重之差（首条为 null）。
 */
@Data
public class BodyRecordVO {

    private Long id;

    private LocalDate date;

    private BigDecimal weight;

    private String note;

    private BigDecimal diff;
}
