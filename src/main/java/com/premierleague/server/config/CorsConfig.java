package com.premierleague.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORS 配置。
 *
 * <h2>白名单来源</h2>
 * 从 {@code app.cors.allowed-origins} 读取（逗号分隔字符串，便于 env var 覆盖）。支持：
 * <ul>
 *   <li>精确 origin：{@code https://admin.example.com}</li>
 *   <li>通配模式：{@code https://*.example.com}（由 {@code allowedOriginPatterns} 支持）</li>
 *   <li>本地开发：{@code http://localhost:*}、{@code http://127.0.0.1:*}</li>
 * </ul>
 *
 * <h2>WeChat 小程序说明</h2>
 * 小程序 {@code wx.request} 不是浏览器同源模型下的请求，不带 Origin（或发送
 * {@code https://servicewechat.com}）——CORS 白名单收紧或置空都不会影响小程序流量。
 * CORS 真正影响的是：浏览器 H5、web 管理后台、开发时的本地网页调试工具。
 *
 * <h2>空列表行为</h2>
 * 列表为空时不注册任何 CORS 映射 → 默认拒绝所有跨源预检。这是安全的 prod 默认值；
 * 需要开放某个 web UI 时通过环境变量 {@code APP_CORS_ALLOWED_ORIGINS=a,b,c} 显式配置即可。
 *
 * <h2>{@code "*"} 通配</h2>
 * 白名单里若出现裸 {@code "*"}，启动时打印 WARN：允许任意来源等同于关闭 CORS 防护，
 * 只应在本地开发或调试期间短暂使用。
 */
@Slf4j
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    /**
     * 用 {@code String} 接收而不是 {@code List<String>}，原因：{@code @Value} 对 yaml list
     * 的绑定不稳定（yaml list 与 comma-separated 的兼容性依赖 ConversionService 行为）；
     * 强制所有调用方用 comma-separated 更明确，也便于 env var 注入。
     */
    public CorsConfig(@Value("${app.cors.allowed-origins:}") String rawOrigins) {
        this.allowedOrigins = parse(rawOrigins);
        if (allowedOrigins.isEmpty()) {
            log.info("[CORS] no allowed-origins configured — cross-origin preflight will be rejected. "
                    + "(WeChat mini-app traffic is unaffected.)");
        } else {
            log.info("[CORS] allowed origin patterns: {}", allowedOrigins);
            if (allowedOrigins.contains("*")) {
                log.warn("[CORS] wildcard \"*\" is in allowed-origins — this disables CORS protection. "
                        + "Tighten for production.");
            }
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type", "Authorization", "X-Session-Token")
                .allowCredentials(false) // header token auth，无 cookie，保持 false
                .maxAge(3600);
    }

    private static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }
}
