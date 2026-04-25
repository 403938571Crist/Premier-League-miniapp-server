package com.premierleague.server.exception;

import com.premierleague.server.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/**
 * 全局异常映射。所有从 @RestController 里冒出来的异常都会被收在这里转成统一 {@link ApiResponse}。
 *
 * <h2>泄露防护</h2>
 * 客户端只拿到"我们主动构造的字符串"或"ref: <8 位 id>"，绝不直接把 {@code e.getMessage()} 吐出去。
 * 服务端日志里打完整堆栈 + errorId，运维拿 errorId 能一秒 grep 到现场。
 * <p>
 * 为什么这么做？{@code e.getMessage()} 常见泄露源：
 * <ul>
 *   <li>{@link DataIntegrityViolationException} → SQL 错误里暴露表/列名</li>
 *   <li>{@link org.hibernate.exception.ConstraintViolationException} → schema 信息</li>
 *   <li>Provider 里的 {@code IOException} → 第三方内网 URL / headers / 偶尔带 key 片段</li>
 *   <li>{@link HttpMessageNotReadableException} → Jackson 反序列化路径 + 用户错误输入</li>
 *   <li>任何 {@link RuntimeException} 抛自第三方库时可能带版本/路径</li>
 * </ul>
 *
 * <h2>例外：我们"主动构造"的异常</h2>
 * {@link DataUnavailableException} / {@link IllegalArgumentException} / {@link IllegalStateException}
 * 都来自自家代码，message 我们写什么就是什么，可以安全直通给客户端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- 自家抛的异常：message 受控，可以直接透传 ----------

    @ExceptionHandler(DataUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataUnavailable(DataUnavailableException e) {
        logger.warn("[DataUnavailable] resource={} msg={}", e.getResource(), e.getMessage());
        ApiResponse<Void> body = ApiResponse.error(
                5030,
                "Data temporarily unavailable for " + e.getResource() + ": " + e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.badRequest(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.conflict(e.getMessage()));
    }

    // ---------- Spring / 框架抛的异常：要净化，不直接透传 ----------

    /** @Valid 校验失败（比如 nickName 超长）。把第一个字段错误翻译成客户端能看懂的一句话。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String firstError = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.badRequest(firstError));
    }

    /** @PathVariable / @RequestParam 类型不匹配（比如 /api/teams/abc 应该是 Long） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String msg = "invalid value for parameter '" + e.getName() + "'";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.badRequest(msg));
    }

    /** body 不是合法 JSON / 字段类型错了。不回 Jackson 具体错误——那里面经常带字段路径。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadJson(HttpMessageNotReadableException e) {
        // 服务端日志留完整信息方便排查，客户端只看到 generic
        logger.warn("[BadRequest] malformed request body: {}",
                e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.badRequest("malformed request body"));
    }

    /** DB 约束冲突（唯一键、外键、非空、长度超限）。message 里会带表/列名，必须拦 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        String errorId = shortErrorId();
        logger.error("[errorId={}] DB integrity violation", errorId, e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.conflict("data conflict (ref: " + errorId + ")"));
    }

    // ---------- 兜底：未知异常 ----------

    /**
     * 任何没被上面分支吃掉的都走这里。绝不把 {@code e.getMessage()} 给客户端——
     * 客户端只能拿到 "Internal server error (ref: xxxxxxxx)"，凭这个 ref
     * 去 server log 里 grep 就能找到完整堆栈和请求 URI。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        String errorId = shortErrorId();
        logger.error("[errorId={}] Unexpected error processing {} {}",
                errorId, request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Internal server error (ref: " + errorId + ")"));
    }

    /** 8 位十六进制 ID——够短方便客户端回报，够随机避免碰撞（16^8 = 43 亿）。 */
    private static String shortErrorId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
