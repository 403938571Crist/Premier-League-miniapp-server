package com.premierleague.server.dto;

import jakarta.validation.constraints.Size;

/**
 * 小程序创建匿名 session 的请求体。
 *
 * <p>约束来源：实体字段长度（AppUser.displayName=100、AppUser.avatarUrl=500）。
 * 没有 @NotBlank 是因为这些字段全是可选——客户端可以只带 deviceId 拉一个空档案再 PUT。
 */
public record AuthSessionRequest(
        @Size(max = 128, message = "deviceId too long")
        String deviceId,

        @Size(max = 100, message = "nickName too long")
        String nickName,

        @Size(max = 500, message = "avatarUrl too long")
        String avatarUrl
) {
}
