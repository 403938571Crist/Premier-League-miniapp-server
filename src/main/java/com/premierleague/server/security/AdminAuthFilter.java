package com.premierleague.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /api/admin/** 接口的轻量鉴权：
 *   - header `X-Admin-Token: <token>` 或 `Authorization: Bearer <token>`
 *   - token 来自环境变量 ADMIN_API_TOKEN（`app.admin.api-token` 属性）
 *   - 校验用 {@link MessageDigest#isEqual(byte[], byte[])} 做常数时间比较，避免时序侧信道
 *   - OPTIONS 预检放行（CORS 预检不会带业务 header）
 *   - token 未配置时锁死所有 admin 请求（fail-closed），避免忘设环境变量导致匿名可调
 *
 * 没引入 Spring Security：admin 接口本来就不多，Security 那一整套 SecurityFilterChain / UserDetailsService
 * 在这个项目里是 over-engineering；一个 Filter + 一个 header 就能达到同样效果，依赖更轻、启动更快。
 */
@Slf4j
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String HEADER_ADMIN_TOKEN = "X-Admin-Token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedTokenBytes;
    private final boolean tokenConfigured;
    private final AtomicBoolean unconfiguredWarningEmitted = new AtomicBoolean(false);
    private final ObjectMapper objectMapper;

    public AdminAuthFilter(
            @Value("${app.admin.api-token:}") String configuredToken,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        String trimmed = configuredToken == null ? "" : configuredToken.trim();
        this.tokenConfigured = !trimmed.isEmpty();
        this.expectedTokenBytes = tokenConfigured
                ? trimmed.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (tokenConfigured) {
            log.info("[AdminAuth] enabled; {} bytes", expectedTokenBytes.length);
        } else {
            log.warn("[AdminAuth] ADMIN_API_TOKEN not set — all /api/admin/** requests will be rejected with 401. "
                    + "Set the env var to enable admin access.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(ADMIN_PATH_PREFIX)) {
            return true;
        }
        // CORS 预检放行
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!tokenConfigured) {
            // fail-closed：token 未配置 → 一律拒绝（避免上线忘设环境变量 → 匿名可调）
            if (unconfiguredWarningEmitted.compareAndSet(false, true)) {
                log.error("[AdminAuth] refusing admin request {} {} — ADMIN_API_TOKEN not configured",
                        request.getMethod(), request.getRequestURI());
            }
            writeUnauthorized(response, "admin endpoints disabled (ADMIN_API_TOKEN not set)");
            return;
        }

        String presented = extractToken(request);
        if (presented == null || presented.isEmpty()) {
            log.warn("[AdminAuth] 401 (missing token) {} {} from {}",
                    request.getMethod(), request.getRequestURI(), clientAddr(request));
            writeUnauthorized(response, "admin token required");
            return;
        }

        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presentedBytes, expectedTokenBytes)) {
            log.warn("[AdminAuth] 401 (bad token) {} {} from {}",
                    request.getMethod(), request.getRequestURI(), clientAddr(request));
            writeUnauthorized(response, "invalid admin token");
            return;
        }

        chain.doFilter(request, response);
    }

    private static String extractToken(HttpServletRequest request) {
        String direct = request.getHeader(HEADER_ADMIN_TOKEN);
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }
        String authz = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authz != null && authz.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authz.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 与其余接口保持同一套 ApiResponse 外壳，前端/运维脚本解析一致
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.unauthorized(message));
    }

    private static String clientAddr(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma >= 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return request.getRemoteAddr();
    }
}
