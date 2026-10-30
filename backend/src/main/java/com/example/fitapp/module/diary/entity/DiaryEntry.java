package com.example.fitapp.module.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一篇日记（ADR 0044）。表结构以迁移脚本 {@code V32__diary.sql} 为准。 */
@Data
@TableName("diary_entry")
public class DiaryEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 这篇的时刻（精确到分，可改成过去）。 */
    private LocalDateTime writtenAt;

    /** 可选标题；没有就是空串。 */
    private String title;

    /** Markdown 原文。 */
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
