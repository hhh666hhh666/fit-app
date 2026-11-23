package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.example.fitapp.module.nutrition.service.EntryTime;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改一条饮食条目：给了哪项改哪项，不给就不动（改食物 = 删了重加；per100 快照不动，实际值重算展示）。
 * 名称只有临时条目能改（没挂食物库的条目，scope #48）；从食物库记的条目名字是那条食物的快照，不能单独改。
 */
@Data
public class MealUpdateDTO {

    @DecimalMin(value = "0.01", message = "份量必须大于 0")
    @DecimalMax(value = "9999.99", message = "份量过大")
    private BigDecimal grams;

    @Size(max = 64, message = "名称不能超过 64 字")
    @Pattern(regexp = ".*\\S.*", message = "名称不能为空")
    private String name;

    /**
     * 按份改份量时的单位与一份多少 g（ADR 0066），和 grams 一起给；
     * 只给 grams 不给这两项＝按克改，条目上的份数快照随之清掉。
     */
    @Size(max = 8, message = "单位过长")
    private String portionUnit;

    @DecimalMin(value = "0.01", message = "一份要大于 0")
    @DecimalMax(value = "99999.99", message = "一份过大")
    private BigDecimal portionGrams;

    /** 时刻（HH:mm，ADR 0059）。早午晚三餐的条目改时刻＝整餐一起改（ADR 0062）。 */
    @Pattern(regexp = EntryTime.PATTERN, message = "时刻格式不正确")
    private String time;
    /** true＝清除时刻（ADR 0062）；和 time 同时给时以清除为准。三餐的条目清除＝整餐一起清。 */
    private Boolean clearTime;
}
