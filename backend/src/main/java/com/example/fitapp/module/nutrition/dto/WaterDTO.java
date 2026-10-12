package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 饮水加减一笔（正=加、负=回退），累计到该日；结果不小于 0。
 */
@Data
public class WaterDTO {

    /** 日期（yyyy-MM-dd，服务端解析）。 */
    @NotBlank(message = "日期不能为空")
    private String date;

    @NotNull(message = "增减量不能为空")
    @Min(value = -5000, message = "单笔增减过大")
    @Max(value = 5000, message = "单笔增减过大")
    private Integer delta;
}
