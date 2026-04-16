package com.premierleague.server.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * HTTP 客户端封装
 */
@Slf4j
@Component
public class HttpClientUtil {
    
    private final WebClient webClient;
    
    public HttpClientUtil() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .compress(true);  // 启用自动解压 gzip/deflate
        
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
}
