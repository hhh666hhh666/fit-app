package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.example.fitapp.module.nutrition.service.EntryTime;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量往某一餐加条目（食物组合「整份加入」用）。
 * 一次事务写成，避免"加了一半"留下半份组合。
 */
@Data
public class MealBatchDTO {

    @NotNull(message = "日期不能为空")
    private LocalDate date;

    @NotBlank(message = "餐次不能为空")
    private String meal;

    @NotEmpty(message = "至少需要一样食物")
    @Valid
    private List<FoodAmountDTO> items;

    /** 时刻（HH:mm，ADR 0059）；不给按默认规则：记今天用现在的钟点，补记按餐次（见 EntryTime）。 */
    @Pattern(regexp = EntryTime.PATTERN, message = "时刻格式不正确")
    private String time;
}
