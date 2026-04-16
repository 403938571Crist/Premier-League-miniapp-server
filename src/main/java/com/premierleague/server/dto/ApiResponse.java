package com.premierleague.server.dto;

/**
 * 通用返回结构 - 对应字段字典 2. 通用返回结构
 */
public record ApiResponse<T>(
        int code,
        String message,
        T data
) {
    // 成功响应
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }
    
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }
    
    // 错误响应
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
    
    // 常用错误
    public static <T> ApiResponse<T> notFound(String resource) {
        return new ApiResponse<>(4004, resource + " not found", null);
    }
    
    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(4000, message, null);
    }
}
