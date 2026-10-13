package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食物组合：一串「食物 ＋ 固定克数」的集合（如「减脂早餐 ＝ 燕麦 50g ＋ 鸡蛋 100g」）。
 * 组合本身只是"批量添加的快捷键"——加入一餐时展开为多条普通 meal_log（ADR 0012）。
 */
@Data
@TableName("food_combo")
public class FoodCombo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 组合名（用户自定）。 */
    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
