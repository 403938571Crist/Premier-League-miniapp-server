package com.premierleague.server.exception;

import com.premierleague.server.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 上游数据源全部失败 → 503 + 语义化 message
     * 前端据此展示"暂时无法获取"+下拉重试，而不是空表。
     */
    @ExceptionHandler(DataUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataUnavailable(DataUnavailableException e) {
        logger.warn("[DataUnavailable] resource={} msg={}", e.getResource(), e.getMessage());
        ApiResponse<Void> body = ApiResponse.error(
                5030,
                "Data temporarily unavailable for " + e.getResource() + ": " + e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        logger.error("Unexpected error: ", e);
        return ApiResponse.error(500, "Internal server error: " + e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.badRequest(e.getMessage());
    }
}
