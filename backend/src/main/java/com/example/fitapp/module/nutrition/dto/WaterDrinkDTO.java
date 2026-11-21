package com.example.fitapp.module.nutrition.dto;

import com.example.fitapp.module.nutrition.service.EntryTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 记一笔饮水，或改一笔（ADR 0059）。新增时 date、ml 必填（服务端校验），时刻不给就用现在的钟点；
 * 修改时给了哪项改哪项，日期不能改。
 */
@Data
public class WaterDrinkDTO {

    /** 日期（yyyy-MM-dd）；只在新增时用。 */
    private String date;

    @Min(value = 1, message = "饮水量要大于 0")
    @Max(value = 5000, message = "单笔饮水过大")
    private Integer ml;

    @Pattern(regexp = EntryTime.PATTERN, message = "时刻格式不正确")
    private String time;
}
