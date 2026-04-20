package com.premierleague.server.service;

import com.premierleague.server.entity.News;
import com.premierleague.server.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * 去重检查服务
 * 使用 MD5(title + sourceType + sourcePublishedAt) 生成指纹
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicateCheckService {
    
    private final NewsRepository newsRepository;
    
    /**
     * 生成去重指纹。
     *
     * 优先级：
     *   1) 有 URL → MD5(url)          最可靠，同一文章 URL 不变
     *   2) 无 URL → MD5(normalizedTitle + "|" + sourceType)
     *              去掉 publishedAt，因为同一文章 feed 里的时间戳可能微调，
     *              用时间会导致同一篇文章反复插入（复现的 "112 条重复" 问题）
     */
    public String generateFingerprint(News news) {
        String raw;

        String url = news.getUrl();
        if (url != null && !url.isBlank()) {
            // 去掉 URL 里的 UTM / RSS tracking 参数，只保留干净路径
            String cleanUrl = url.replaceAll("[?&](utm_[^&]*|at_medium=[^&]*|at_campaign=[^&]*|at_custom[^&]*)", "")
                                 .replaceAll("[?]$", "")
                                 .trim();
            raw = cleanUrl;
        } else {
            String normalizedTitle = news.getTitle()
                    .replaceAll("[\\s\\p{Punct}\\uff01\\u3002\\uff0c\\u3001\\uff1b\\uff1a\\u201c\\u201d\\u2018\\u2019\\u3010\\u3011\\u300a\\u300b\\uff1f]", "")
                    .toLowerCase();
            raw = normalizedTitle + "|" + (news.getSourceType() != null ? news.getSourceType() : "");
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(raw.hashCode());
        }
    }
    
    /**
     * 检查是否重复
     */
    public boolean isDuplicate(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return false;
        }
        return newsRepository.existsById(fingerprint);
    }
    
    /**
     * 检查并设置指纹
     * @return true 如果是重复数据
     */
    public boolean checkAndSetFingerprint(News news) {
        String fingerprint = generateFingerprint(news);
        
        Optional<News> existing = newsRepository.findById(fingerprint);
        if (existing.isPresent()) {
            log.debug("Duplicate news detected: fingerprint={}, title={}", 
                    fingerprint, news.getTitle());
            return true;
        }
        
        news.setFingerprint(fingerprint);
        news.setId(fingerprint); // 使用指纹作为 ID
        return false;
    }
}
