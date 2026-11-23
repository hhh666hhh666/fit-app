package com.example.fitapp.module.nutrition.dto;

import com.example.fitapp.module.nutrition.service.EntryTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 改时刻（ADR 0062）：早午晚三餐给 date ＋ meal，整餐一起改；加餐给 ids，选中的几条一起改。
 * time 不给（或为 null）＝清除时刻。
 */
@Data
public class MealTimeDTO {
    /** 三餐用：日期（yyyy-MM-dd）。 */
    private String date;
    /** breakfast / lunch / dinner / snack；snack 时按 ids 改。 */
    @NotBlank(message = "餐次不能为空")
    @Pattern(regexp = "breakfast|lunch|dinner|snack", message = "餐次不正确")
    private String meal;
    /** 加餐用：要改的条目。 */
    @Size(max = 200, message = "一次最多改 200 条")
    private List<Long> ids;
    /** HH:mm；null＝清除。 */
    @Pattern(regexp = EntryTime.PATTERN, message = "时刻格式不正确")
    private String time;
}
