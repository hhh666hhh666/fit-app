package com.example.fitapp.module.profile.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保存个人档案（四项一次性提交、整体覆盖）：性别／出生日期／身高／生活活动档。
 * 四项都必填——缺任何一项就算不出基础代谢，也就没有「每日总消耗」可显示（ADR 0029）。
 */
@Data
public class ProfileSaveDTO {

    @NotNull(message = "请选择性别")
    @Pattern(regexp = "male|female", message = "性别取值不正确")
    private String sex;

    @NotNull(message = "请填写出生日期")
    @Past(message = "出生日期应早于今天")
    private LocalDate birthDate;

    @NotNull(message = "请填写身高")
    @DecimalMin(value = "100", message = "身高数值不正确")
    @DecimalMax(value = "250", message = "身高数值不正确")
    @Digits(integer = 3, fraction = 1, message = "身高最多保留一位小数")
    private BigDecimal heightCm;

    @NotNull(message = "请选择生活活动量")
    @Pattern(regexp = "sedentary|light|active", message = "生活活动量取值不正确")
    private String activityLevel;
}
