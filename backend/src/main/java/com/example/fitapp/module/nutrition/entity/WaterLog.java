package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 一笔饮水（ADR 0059）：饮水的真相。当天各笔之和另存在 {@link DietDaily#getWaterMl()} 当缓存，
 * 每次增删改都要同一事务里重算（{@code DietService.syncWaterTotal}）。
 */
@Data
@TableName("water_log")
public class WaterLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate logDate;

    /** 这一笔喝了多少（ml）。 */
    private Integer ml;

    /** 时刻；从旧的每日累计迁来的为 null。 */
    private LocalTime drunkAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
