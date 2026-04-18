package com.premierleague.server.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FlareSolverr 客户端 —— 绕过 Cloudflare JS challenge。
 *
 * 需要本地/同网起一个 FlareSolverr 容器：
 *   docker run -d --name flaresolverr -p 8191:8191 \
 *     ghcr.io/flaresolverr/flaresolverr:latest
 *
 * 使用方式：向 http://host:8191/v1 POST 一个 command 对象，
 * FlareSolverr 会启动 headless Chrome 把页面过完 CF 挑战，
 * 然后把最终的 HTML 返回。
 *
 * 未启用（scraper.flaresolverr.enabled=false 或 url 为空）时 isEnabled() 返回 false，
 * 调用方可直接走直连路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlareSolverrClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClientUtil httpClient;

    @Value("${scraper.flaresolverr.enabled:false}")
    private boolean enabled;

    @Value("${scraper.flaresolverr.url:}")
    private String endpoint;

    @Value("${scraper.flaresolverr.max-timeout-ms:60000}")
    private int maxTimeoutMs;

    public boolean isEnabled() {
        return enabled && endpoint != null && !endpoint.isBlank();
    }

    /**
     * 通过 FlareSolverr 代理 GET 一个 URL，返回最终 HTML（已过 Cloudflare）。
     * 失败返回 null，调用方自行降级。
     */
    public String get(String targetUrl) {
        if (!isEnabled()) {
            log.debug("[FlareSolverr] disabled, skip");
            return null;
        }
        try {
            String payload = MAPPER.writeValueAsString(java.util.Map.of(
                    "cmd", "request.get",
                    "url", targetUrl,
                    "maxTimeout", maxTimeoutMs
            ));

            log.info("[FlareSolverr] resolving {} via {}", targetUrl, endpoint);

            String body = httpClient.postJson(endpoint, payload);
            if (body == null || body.isEmpty()) {
                log.warn("[FlareSolverr] empty response");
                return null;
            }

            JsonNode root = MAPPER.readTree(body);
            String status = root.path("status").asText("");
            if (!"ok".equalsIgnoreCase(status)) {
                log.warn("[FlareSolverr] non-ok status: {} msg={}",
                        status, root.path("message").asText(""));
                return null;
            }
            String html = root.path("solution").path("response").asText(null);
            if (html == null || html.isBlank()) {
                log.warn("[FlareSolverr] empty solution.response");
                return null;
            }
            log.info("[FlareSolverr] resolved {} ({} chars)", targetUrl, html.length());
            return html;
        } catch (Exception e) {
            log.error("[FlareSolverr] resolve failed for {}: {}", targetUrl, e.getMessage(), e);
            return null;
        }
    }
}
