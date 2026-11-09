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

    /** 识别入口：label＝食物库营养成分表入库，meal＝记一餐 AI 识别（V40 起；老记录为空）。 */
    private String kind;

    /** 模型档（如 qwen3-vl-8b-instruct）。 */
    private String model;

    /** 服务商地址（不含 Key）。 */
    private String endpoint;

    /** 当时的思考开关配置。 */
    private String thinkingMode;

    /** 当时的思考等级配置（空＝没传）。 */
    private String reasoningEffort;

    /** 输入 token（含缓存命中部分）。 */
    private Integer tokensIn;

    /** 输出 token。 */
    private Integer tokensOut;

    /** 输入 token 中命中缓存的部分。 */
    private Integer cachedTokens;

    /** 输出 token 里用于思考的部分（服务商给了才有）。 */
    private Integer reasoningTokens;

    /** 调用耗时（毫秒）。 */
    private Integer durationMs;

    /** 发给模型的图片字节数。 */
    private Integer imageBytes;

    /** 图片格式。 */
    private String imageMime;

    /** 模型结束原因（stop / length …）。 */
    private String finishReason;

    /** 服务商返回的请求 ID。 */
    private String upstreamId;

    /** 是否识别成功。 */
    private Boolean success;

    /** 识别出的名称（成功时）。 */
    private String resultName;

    /** 失败原因（给用户看的那句）。 */
    private String errorMessage;

    /** 模型回答的那段文字（响应里 message.content）。列表接口不带它（详情才查）。 */
    private String rawOutput;

    /** 发给服务商的请求体（图片 base64 已换成占位；Key 在请求头里，不在这里）。列表接口不带。 */
    private String requestJson;

    /** 服务商返回的完整响应；失败时为 HTTP 状态＋报错正文。列表接口不带。 */
    private String responseJson;

    private LocalDateTime createdAt;
}
