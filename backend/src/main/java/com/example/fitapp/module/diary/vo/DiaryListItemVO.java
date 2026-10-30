package com.example.fitapp.module.diary.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 列表里的一篇：正文只带开头一段 Markdown 原文，由前端去掉语法后显示。 */
@Data
public class DiaryListItemVO {

    private Long id;
    private LocalDateTime writtenAt;
    private String title;
    private String preview;
}
