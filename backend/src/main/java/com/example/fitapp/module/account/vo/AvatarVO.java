package com.example.fitapp.module.account.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 历史头像里的一项（ADR 0051）：宽高与裁剪框都按摆正后的原图像素算。 */
@Data
public class AvatarVO {
    private Long id;
    private Integer width;
    private Integer height;
    private Integer cropX;
    private Integer cropY;
    private Integer cropSize;
    /** 是不是当前头像 */
    private boolean current;
    private LocalDateTime usedAt;
}
