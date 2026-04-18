package com.premierleague.server.exception;

/**
 * 当聚合数据的所有上游 provider 都失败/返回空时抛出。
 * 被 GlobalExceptionHandler 映射为 HTTP 503 + 统一 ApiResponse 结构，
 * 避免让前端拿到 "200 OK + 空数组" 的哑铃状态。
 */
public class DataUnavailableException extends RuntimeException {

    private final String resource;

    public DataUnavailableException(String resource, String message) {
        super(message);
        this.resource = resource;
    }

    public String getResource() {
        return resource;
    }
}
