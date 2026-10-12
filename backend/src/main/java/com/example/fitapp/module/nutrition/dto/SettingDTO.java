package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设置全量提交：图片压缩等级 + 饮水步进（必填），饮食目标五值（可空 = 不设/清除）。
 */
@Data
public class SettingDTO {

    @NotBlank(message = "图片压缩等级不能为空")
    @Pattern(regexp = "lossless|high|medium|low", message = "图片压缩等级不正确")
    private String photoQuality;

    @NotNull(message = "饮水步进不能为空")
    @Min(value = 10, message = "饮水步进至少 10ml")
    @Max(value = 2000, message = "饮水步进过大")
    private Integer waterStepMl;

    @DecimalMin(value = "0", message = "目标不能为负")
    private BigDecimal targetKcal;

    @DecimalMin(value = "0", message = "目标不能为负")
    private BigDecimal targetProtein;

    @DecimalMin(value = "0", message = "目标不能为负")
    private BigDecimal targetCarb;

    @DecimalMin(value = "0", message = "目标不能为负")
    private BigDecimal targetFat;

    @Min(value = 0, message = "目标不能为负")
    @Max(value = 10000, message = "饮水目标过大")
    private Integer targetWater;
}
