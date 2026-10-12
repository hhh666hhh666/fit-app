package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日饮水累计（每用户每天至多一行）。
 */
@Data
@TableName("diet_daily")
public class DietDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate logDate;

    /** 当日饮水累计（ml）。 */
    private Integer waterMl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
