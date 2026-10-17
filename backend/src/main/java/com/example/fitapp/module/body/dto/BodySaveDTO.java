package com.example.fitapp.module.body.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 录入（或修改）一次测定：体重与四围记哪项填哪项（至少一项，服务端校验）；时刻缺省为现在。
 */
@Data
public class BodySaveDTO {

    private LocalDateTime measuredAt;

    @DecimalMin(value = "20", message = "体重数值不正确")
    @DecimalMax(value = "400", message = "体重数值不正确")
    @Digits(integer = 3, fraction = 2, message = "体重最多保留两位小数")
    private BigDecimal weight;

    /** 体脂率 %：只记录、不参与计算；1 位小数（体脂秤给的就是 1 位）。 */
    @DecimalMin(value = "3", message = "体脂率数值不正确")
    @DecimalMax(value = "75", message = "体脂率数值不正确")
    @Digits(integer = 2, fraction = 1, message = "体脂率最多保留一位小数")
    private BigDecimal bodyFat;

    @DecimalMin(value = "10", message = "围度数值不正确")
    @DecimalMax(value = "250", message = "围度数值不正确")
    private BigDecimal chest;

    @DecimalMin(value = "10", message = "围度数值不正确")
    @DecimalMax(value = "250", message = "围度数值不正确")
    private BigDecimal waist;

    @DecimalMin(value = "10", message = "围度数值不正确")
    @DecimalMax(value = "250", message = "围度数值不正确")
    private BigDecimal hip;

    @DecimalMin(value = "10", message = "围度数值不正确")
    @DecimalMax(value = "250", message = "围度数值不正确")
    private BigDecimal arm;

    @Size(max = 64, message = "备注过长")
    private String note;
}
