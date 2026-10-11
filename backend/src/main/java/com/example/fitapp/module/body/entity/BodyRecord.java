package com.example.fitapp.module.body.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 身体档案的一次测定。表结构以迁移脚本 {@code V8__body_record.sql} 为准。
 * 体重与四围均可空（记哪项填哪项，至少一项）；允许同日多条（note 区分场景）。
 */
@Data
@TableName("body_record")
public class BodyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 测定日期（日期粒度）。 */
    private LocalDate measuredAt;

    /** 体重 kg。 */
    private BigDecimal weight;

    /** 胸围 cm。 */
    private BigDecimal chest;

    /** 腰围 cm。 */
    private BigDecimal waist;

    /** 臀围 cm。 */
    private BigDecimal hip;

    /** 臂围 cm。 */
    private BigDecimal arm;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
