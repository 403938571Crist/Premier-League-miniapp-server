package com.premierleague.server.dto;

import jakarta.validation.constraints.Size;

/**
 * PUT /api/users/me 更新用户档案的请求体。
 * 字段全部可选（null = 不改），有值才受长度约束。
 */
public record UpdateProfileRequest(
        @Size(max = 100, message = "nickName too long")
        String nickName,

        @Size(max = 500, message = "avatarUrl too long")
        String avatarUrl
) {
}
