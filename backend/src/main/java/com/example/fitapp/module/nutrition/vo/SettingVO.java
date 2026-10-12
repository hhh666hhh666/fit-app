package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 饮食设置与目标（行不存在时返回默认：medium / 250ml / 目标全空）。
 */
@Data
public class SettingVO {

    private String photoQuality;

    private Integer waterStepMl;

    private BigDecimal targetKcal;

    private BigDecimal targetProtein;

    private BigDecimal targetCarb;

    private BigDecimal targetFat;

    private Integer targetWater;
}
