package com.example.fitapp.module.diary.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 相册里的一张日记图片（只出元数据，图片由前端逐个取）。 */
@Data
public class DiaryPhotoVO {

    private Long id;
    private Integer sizeBytes;
    private LocalDateTime createdAt;
}
