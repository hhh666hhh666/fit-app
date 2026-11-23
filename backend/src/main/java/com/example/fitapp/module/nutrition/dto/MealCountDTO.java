package com.example.fitapp.module.nutrition.dto;

import com.example.fitapp.module.nutrition.service.EntryTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 边吃边数吃完了（ADR 0067）：一次事务把数过的几种都记进同一餐，每种落成一条普通条目。
 * 食物库的食物给 foodId；吃过的临时条目给名字和每 100 营养（同临时条目口径）。
 */
@Data
public class MealCountDTO {

    @NotBlank(message = "日期不能为空")
    private String date;

    @NotBlank(message = "餐次不能为空")
    @Pattern(regexp = "breakfast|lunch|dinner|snack", message = "餐次不正确")
    private String meal;

    /** 开始数的那一刻（HH:mm）；三餐已有时刻的沿用那一餐的（ADR 0062）。 */
    @Pattern(regexp = EntryTime.PATTERN, message = "时刻格式不正确")
    private String time;

    @NotEmpty(message = "至少要数过一种")
    @Valid
    private List<MealCountItemDTO> items;
}
