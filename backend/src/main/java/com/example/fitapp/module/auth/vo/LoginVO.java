package com.example.fitapp.module.auth.vo;

/**
 * 注册 / 登录成功后的返回：token ＋ 基本信息。
 */
public record LoginVO(String token, Long userId, String username) {
}
