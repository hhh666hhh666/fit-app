package com.example.fitapp.module.diary.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 一篇日记的全文。 */
@Data
public class DiaryEntryVO {

    private Long id;
    private LocalDateTime writtenAt;
    private String title;
    private String content;
    private LocalDateTime updatedAt;
}
