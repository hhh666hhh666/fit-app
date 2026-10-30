package com.example.fitapp.module.trash.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 回收站列表的一行。 */
@Data
public class TrashItemVO {
    private Long id;
    private String type;
    private String title;
    private String summary;
    private LocalDateTime deletedAt;
    private Long remainingDays;
    private Boolean hasThumb;
}
