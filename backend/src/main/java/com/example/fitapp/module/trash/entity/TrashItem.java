package com.example.fitapp.module.trash.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 回收站里的一条（ADR 0047）。表结构以迁移脚本 {@code V35__trash.sql} 为准。 */
@Data
@TableName("trash_item")
public class TrashItem {

    public static final String WORKOUT = "workout";
    public static final String DIARY = "diary";
    public static final String MEAL_PHOTO = "meal_photo";
    public static final String DIARY_PHOTO = "diary_photo";
    public static final String BODY = "body";
    public static final String PLAN = "plan";
    public static final String EXERCISE = "exercise";
    public static final String FOOD = "food";
    public static final String SESSION = "session";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    /** 原来那一行的 id；恢复时按它写回，弱引用和记录引用才接得上。 */
    private Long sourceId;

    private String title;

    private String summary;

    private String snapshot;

    /** 快照带着的照片占用：文件还在磁盘上，继续计入配额。 */
    private Long photoBytes;

    private String dedupeKey;

    private LocalDateTime deletedAt;
}
