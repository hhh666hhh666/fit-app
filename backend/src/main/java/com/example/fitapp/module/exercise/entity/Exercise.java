package com.example.fitapp.module.exercise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动作库条目。表结构以迁移脚本 {@code V3__exercise.sql} 为准。
 * user_id 为 NULL 表示预设动作；archived=1 表示已下架（历史仍显示）。
 */
@Data
@TableName("exercise")
public class Exercise {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String muscle;

    /** 记录口径：`weight_reps`＝重量×次数（默认）／`floors`＝层数（爬楼）。见 ADR 0025。 */
    private String metric;

    // ── 动作资料（ADR 0055，V42）：来自开源动作数据集，自定义动作全为空 ──

    /** 数据集编号；导入时据此认出同一条。 */
    private String sourceId;

    private String nameEn;

    /** 器械、目标肌：中文。 */
    private String equipment;

    private String target;

    /** 辅助肌群：中文，顿号分隔。 */
    private String secondary;

    /** 动作要领：每步一行。 */
    private String steps;

    /** 动图文件键：{@code exercise-media/{media}.gif} 与 {@code .jpg}。 */
    private String media;

    private Boolean archived;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
