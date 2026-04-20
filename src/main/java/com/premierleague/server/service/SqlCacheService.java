package com.premierleague.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.premierleague.server.entity.ApiCache;
import com.premierleague.server.repository.ApiCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQL 持久化缓存服务（L2 缓存）。
 *
 * 使用方式：
 * <pre>
 *   // 读
 *   Optional<List<PlayerStat>> hit = sqlCache.get("topScorers:20", new TypeReference<>(){});
 *   if (hit.isPresent()) return hit.get();
 *
 *   // 写
 *   List<PlayerStat> result = expensiveExternalCall();
 *   sqlCache.set("topScorers:20", result, Duration.ofHours(2));
 *   return result;
 * </pre>
 *
 * 缓存键约定（domain:subkey）：
 *   topScorers:{limit}     射手榜   TTL 2h
 *   topAssists:{limit}     助攻榜   TTL 2h
 *   standings:{type}       积分榜   TTL 30min
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlCacheService {

    private final ApiCacheRepository repo;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ──────────────────────── 公开 API ────────────────────────

    /**
     * 读缓存。未命中或已过期返回 empty。
     *
     * @param key       缓存键
     * @param typeRef   Jackson TypeReference，用于泛型反序列化
     */
    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        try {
            Optional<ApiCache> cached = repo.findValidByKey(key, LocalDateTime.now());
            if (cached.isEmpty()) {
                log.debug("[SqlCache] MISS  key={}", key);
                return Optional.empty();
            }
            ApiCache entry = cached.get();
            // 更新命中计数（非关键路径，fire-and-forget）
            entry.setHitCount(entry.getHitCount() + 1);
            repo.save(entry);

            T value = objectMapper.readValue(entry.getCacheValue(), typeRef);
            log.debug("[SqlCache] HIT   key={} hitCount={}", key, entry.getHitCount());
            return Optional.of(value);
        } catch (Exception e) {
            log.warn("[SqlCache] read error key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写缓存（upsert）。序列化失败只打 warn，不抛异常。
     *
     * @param key   缓存键
     * @param value 任意可 JSON 序列化的对象
     * @param ttl   过期时长
     */
    @Transactional
    public void set(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            LocalDateTime now = LocalDateTime.now();
            ApiCache entry = ApiCache.builder()
                    .cacheKey(key)
                    .cacheValue(json)
                    .createdAt(now)
                    .expiresAt(now.plus(ttl))
                    .hitCount(0)
                    .build();
            repo.save(entry);
            log.debug("[SqlCache] SET   key={} ttl={}min len={}",
                    key, ttl.toMinutes(), json.length());
        } catch (Exception e) {
            log.warn("[SqlCache] write error key={}: {}", key, e.getMessage());
        }
    }

    /** 删除单条缓存 */
    @Transactional
    public void evict(String key) {
        repo.deleteById(key);
        log.info("[SqlCache] EVICT key={}", key);
    }

    /** 按前缀批量删（如清掉所有 "topScorers:" 条目） */
    @Transactional
    public int evictByPrefix(String prefix) {
        int n = repo.deleteByKeyPrefix(prefix);
        log.info("[SqlCache] EVICT prefix={}* count={}", prefix, n);
        return n;
    }

    /** 返回当前有效缓存概要列表（监控用） */
    public List<Map<String, Object>> summary() {
        return repo.findSummaryValid(LocalDateTime.now()).stream()
                .map(row -> Map.of(
                        "key",       row[0],
                        "createdAt", row[1].toString(),
                        "expiresAt", row[2].toString(),
                        "hitCount",  row[3]
                ))
                .toList();
    }

    // ──────────────────────── 定时清理 ────────────────────────

    /**
     * 每小时整点清理过期行，防止表无限增长
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void cleanExpired() {
        int n = repo.deleteExpired(LocalDateTime.now());
        if (n > 0) {
            log.info("[SqlCache] Cleaned {} expired rows", n);
        }
    }
}
