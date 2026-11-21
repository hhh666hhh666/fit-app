package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
}
