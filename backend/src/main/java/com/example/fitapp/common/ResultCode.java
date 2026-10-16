package com.example.fitapp.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数不正确"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源已存在"),
    BUSINESS_ERROR(1000, "业务处理失败"),
    /** 基础设施瞬时故障（共用 MySQL 容器重启、连接池整体失效等）：写路径已自动重试过，仍失败才落到这里。 */
    SERVICE_UNAVAILABLE(503, "服务正在重启，请稍后重试"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
