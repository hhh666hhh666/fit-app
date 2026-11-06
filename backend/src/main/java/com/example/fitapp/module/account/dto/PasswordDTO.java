package com.example.fitapp.module.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 改密码：要旧密码；新密码规则与注册一致（ADR 0050）。 */
@Data
public class PasswordDTO {

    @NotBlank(message = "请输入旧密码")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
    private String newPassword;
}
