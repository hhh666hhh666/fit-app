package com.example.fitapp.module.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个人档案：基本不变的身体基础信息，一个用户一份。表结构以迁移脚本 {@code V28__user_profile.sql} 为准。
 *
 * <p>与「身体档案」（`body_record`）区分：那边是体重／体脂／围度的**测定序列**，这边是身高性别这类不变值。
 * **体重不进这张表**，始终来自 `body_record`；算某天的消耗时取那天结束前最后一条（ADR 0042）。</p>
 */
@Data
@TableName("user_profile")
public class UserProfile {

    /** 主键即用户 id（一个用户至多一份档案）。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 性别：`male` / `female`（Mifflin-St Jeor 公式需要）。 */
    private String sex;

    /** 出生日期——**存日期不存年龄**：年龄每年会变，存数字必然过期且直接进基础代谢。 */
    private LocalDate birthDate;

    /** 身高 cm。 */
    private BigDecimal heightCm;

    /** 生活活动档：`sedentary` / `light` / `active`（只含日常生活，不含专门训练）。 */
    private String activityLevel;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
