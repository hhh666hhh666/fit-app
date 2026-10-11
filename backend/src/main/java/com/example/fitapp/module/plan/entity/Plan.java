package com.example.fitapp.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 训练计划。表结构以迁移脚本 {@code V6__plan.sql} 为准。
 * 同用户内计划名不重名（服务端校验）。
 */
@Data
@TableName("plan")
public class Plan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
