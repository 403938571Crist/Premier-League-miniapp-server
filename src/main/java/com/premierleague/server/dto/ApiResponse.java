package com.premierleague.server.dto;

public record ApiResponse<T>(
        int code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }

    public static <T> ApiResponse<T> notFound(String resource) {
        return new ApiResponse<>(4004, resource + " not found", null);
    }

    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(4000, message, null);
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(4001, message, null);
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return new ApiResponse<>(4003, message, null);
    }

    public static <T> ApiResponse<T> conflict(String message) {
        return new ApiResponse<>(4009, message, null);
    }
}
