package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 从记一餐列表里移除的一种临时条目；移除之后又记了同样的一条，它会重新出现。 */
@Data
@TableName("meal_pick_hidden")
public class MealPickHidden {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 名字与每 100 营养拼成的键（{@code MealPickService.keyOf}）。 */
    private String pickKey;

    private LocalDateTime hiddenAt;
}
