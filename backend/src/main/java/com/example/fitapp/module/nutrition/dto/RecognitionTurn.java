package com.example.fitapp.module.nutrition.dto;

/**
 * 记一餐识别的一段对话（ADR 0060）：{@code assistant}＝模型上一轮的原样输出，{@code user}＝主人的回复。
 * 服务端不存对话，前端每轮把前面的对话连同照片一起发回来。
 */
public record RecognitionTurn(String role, String content) {
}
