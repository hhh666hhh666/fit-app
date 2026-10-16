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

    private Boolean archived;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
