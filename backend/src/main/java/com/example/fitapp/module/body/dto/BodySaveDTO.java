package com.example.fitapp.module.body.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 录入一次测定：体重与四围记哪项填哪项（至少一项，服务端校验）；日期缺省为今天。
 */
@Data
public class BodySaveDTO {

    private LocalDate measuredAt;

    @DecimalMin(value = "20", message = "体重数值不正确")
    @DecimalMax(value = "400", message = "体重数值不正确")
    private BigDecimal weight;

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
