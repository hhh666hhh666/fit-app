package com.example.fitapp.module.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端用户列表的一行（只读，ADR 0048）。最近记录时间取训练开始和饮食条目创建里最晚的一个。 */
@Data
public class AdminUserVO {

    private Long id;
    private String username;
    private Boolean admin;
    private LocalDateTime createdAt;
    private Long workoutCount;
    private Long mealCount;
    private LocalDateTime lastRecordAt;
}
