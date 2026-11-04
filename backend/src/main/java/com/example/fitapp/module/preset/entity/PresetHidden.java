package com.example.fitapp.module.preset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户对一条已下架预置的隐藏（ADR 0048）：只对这个用户不显示，那一行预置不动。
 * 表结构以迁移脚本 {@code V36__admin_and_preset_archive.sql} 为准。
 */
@Data
@TableName("preset_hidden")
public class PresetHidden {

    public static final String FOOD = "food";
    public static final String EXERCISE = "exercise";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** {@link #FOOD} 或 {@link #EXERCISE}。 */
    private String kind;

    private Long refId;

    private LocalDateTime createdAt;
}
