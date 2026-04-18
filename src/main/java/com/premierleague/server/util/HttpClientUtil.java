package com.premierleague.server.util;

import io.netty.resolver.NoopAddressResolverGroup;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP 客户端封装
 *
 * 支持通过配置走本机代理（dev 环境访问被 GFW 污染的域名，如 pulselive、fbref 用）：
 *   application.yml:
 *     http.proxy.host: 127.0.0.1
 *     http.proxy.port: 7890
 *     http.proxy.type: SOCKS5          # 或 HTTP
 *   或启动参数 -Dhttp.proxy.host=... -Dhttp.proxy.port=...
 *
 * 不配就走直连，生产部署（云托管出海）不需要开。
 */
@Slf4j
@Component
public class HttpClientUtil {

    @Value("${http.proxy.host:}")
    private String proxyHost;

    @Value("${http.proxy.port:0}")
    private int proxyPort;

    /** SOCKS5 / HTTP，默认 SOCKS5（Clash/V2Ray 通用） */
    @Value("${http.proxy.type:SOCKS5}")
    private String proxyType;

    private WebClient webClient;

    @PostConstruct
    void init() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .compress(true);  // 启用自动解压 gzip/deflate

        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            ProxyProvider.Proxy kind = "HTTP".equalsIgnoreCase(proxyType)
                    ? ProxyProvider.Proxy.HTTP
                    : ProxyProvider.Proxy.SOCKS5;
            final String host = proxyHost;
            final int port = proxyPort;
            httpClient = httpClient
                    .proxy(spec -> spec.type(kind).host(host).port(port))
                    // 禁用本地 DNS：否则 Netty 会先把 hostname 解析成 IP（在 CN 被 GFW 污染成 28.x）
                    // 再给代理发 CONNECT <ip>:443。NoopResolver 让 hostname 原样透传，代理侧解析。
                    .resolver(NoopAddressResolverGroup.INSTANCE);
            log.info("[HttpClientUtil] Proxy enabled: {}://{}:{} (remote DNS via proxy)",
                    kind, proxyHost, proxyPort);
        } else {
            log.info("[HttpClientUtil] No proxy configured (direct connection)");
        }

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8")
                .build();
    }
    
    /**
     * GET 请求获取字符串
     */
    public String get(String url) {
        try {
            return webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.error("HTTP GET failed: {}", url, e);
            return null;
        }
    }
    
    /**
     * GET 请求，返回原始字节（用于 GBK 等非 UTF-8 页面）
     */
    public byte[] getBytes(String url) {
        try {
            return webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.error("HTTP GET bytes failed: {}", url, e);
            return null;
        }
    }

    /**
     * GET 请求（带认证头）
     */
    public String getWithAuth(String url, String authHeader) {
        try {
            return webClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.error("HTTP GET with auth failed: {}", url, e);
            return null;
        }
    }

    /**
     * GET 请求（带自定义 headers，用于需要特殊 User-Agent 的场景，如 Reddit）
     */
    public String getWithHeaders(String url, Map<String, String> headers) {
        try {
            var spec = webClient.get().uri(url);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                spec = spec.header(entry.getKey(), entry.getValue());
            }
            return spec.retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.error("HTTP GET with headers failed: {}", url, e);
            return null;
        }
    }

    /**
     * POST application/json，用于 FlareSolverr 等 JSON-RPC 风格接口
     */
    public String postJson(String url, String jsonBody) {
        try {
            return webClient.post().uri(url)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(90));
        } catch (Exception e) {
            log.error("HTTP POST JSON failed: {}", url, e);
            return null;
        }
    }

    /**
     * POST x-www-form-urlencoded，用于 understat 这种老派站点的 AJAX 端点
     */
    public String postForm(String url, String formBody, Map<String, String> headers) {
        try {
            var spec = webClient.post().uri(url)
                    .header("Content-Type", "application/x-www-form-urlencoded");
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    spec = spec.header(entry.getKey(), entry.getValue());
                }
            }
            return spec.bodyValue(formBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.error("HTTP POST form failed: {}", url, e);
            return null;
        }
    }
}
