package com.example.fitapp.module.account.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 裁剪框：正方形，坐标按摆正后的原图像素算（ADR 0051）。出界的部分服务端会收进图内。 */
@Data
public class CropDTO {

    @NotNull
    @Min(0)
    private Integer x;

    @NotNull
    @Min(0)
    private Integer y;

    @NotNull
    @Min(1)
    private Integer size;
}
