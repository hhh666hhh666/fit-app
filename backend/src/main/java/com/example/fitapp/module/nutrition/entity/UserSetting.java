package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 饮食设置与目标（每用户一行；行不存在时读取按默认值呈现，首次写入才落行）。
 */
@Data
@TableName("user_setting")
public class UserSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 图片压缩：lossless/high/medium/low。 */
    private String photoQuality;

    /** 饮水步进（ml）。 */
    private Integer waterStepMl;

    private BigDecimal targetKcal;

    private BigDecimal targetProtein;

    private BigDecimal targetCarb;

    private BigDecimal targetFat;

    /** 每日饮水目标（ml）。 */
    private Integer targetWater;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
