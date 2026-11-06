package com.example.fitapp.module.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 改用户名：规则与注册一致（RegisterDTO），要带当前密码确认（ADR 0050）。 */
@Data
public class UsernameDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需为 3-32 位")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名仅支持字母、数字、下划线")
    private String username;

    @NotBlank(message = "请输入当前密码")
    private String password;
}
