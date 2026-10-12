package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每餐一张的照片（重拍即替换；每用户 + 日期 + 餐次至多一行）。
 */
@Data
@TableName("meal_photo")
public class MealPhoto {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate logDate;

    private String meal;

    private String mime;

    private Integer sizeBytes;

    /** 图片数据（前端已压缩）。 */
    private byte[] image;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
