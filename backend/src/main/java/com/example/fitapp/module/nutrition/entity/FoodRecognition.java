package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签识别用量流水（ADR 0018）：每次识别调用一条，只增不改不删。
 *
 * <p>失败的调用也记——token 真实花了。cached_tokens 是输入 token 里命中隐式缓存的
 * 部分（单价不同，必须单列，混在一起算不清）。本表不折算费用（拍定口径）。</p>
 */
@Data
@TableName("food_recognition")
public class FoodRecognition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 模型档（如 qwen3-vl-8b-instruct）。 */
    private String model;

    /** 输入 token（含缓存命中部分）。 */
    private Integer tokensIn;

    /** 输出 token。 */
    private Integer tokensOut;

    /** 输入 token 中命中缓存的部分。 */
    private Integer cachedTokens;

    /** 调用耗时（毫秒）。 */
    private Integer durationMs;

    /** 是否识别成功。 */
    private Boolean success;

    private LocalDateTime createdAt;
}
