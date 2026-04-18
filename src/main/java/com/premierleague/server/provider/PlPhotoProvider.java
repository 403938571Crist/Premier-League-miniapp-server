package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 给 Understat / Fbref 等外部源补齐球员头像。
 *
 * 原理：拿球员英文名去 pulselive 的 PL 搜索拿到 Opta ID (altIds.opta = "p{id}")，
 *       再拼成官方 CDN URL:
 *         https://resources.premierleague.com/premierleague/photos/players/250x250/p{id}.png
 *
 * 搜索 API（公开、跨域可调）：
 *   GET https://footballapi.pulselive.com/search/PremierLeague
 *         ?terms={name}&type=player&size=3&start=0&fullObjectResponse=true
 *
 * 响应结构 (节选):
 *   {"hits":{"hit":[{"response":{
 *       "altIds":{"opta":"p223094"},
 *       "name":{"display":"Erling Haaland","first":"Erling","last":"Haaland"},
 *       ...
 *   }}]}}
 *
 * 进程内缓存 name → photoUrl，命中不了的记录 "" 防止重复查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlPhotoProvider {

    private static final String SEARCH_URL =
            "https://footballapi.pulselive.com/search/PremierLeague"
                    + "?terms=%s&type=player&size=3&start=0&fullObjectResponse=true";

    private static final String PHOTO_URL_TMPL =
            "https://resources.premierleague.com/premierleague/photos/players/250x250/%s.png";

    private final HttpClientUtil http;
    private final ObjectMapper mapper = new ObjectMapper();

    /** name → photoUrl （空串代表查过但没找到，避免重复调用） */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 查该球员的官方头像 URL，查不到返回 null。
     * 失败不抛异常，降级到 null 让前端走 initials 占位。
     */
    public String findPhotoUrl(String playerName) {
        if (playerName == null || playerName.isBlank()) return null;
        String key = playerName.trim();

        String hit = cache.get(key);
        if (hit != null) {
            return hit.isEmpty() ? null : hit;
        }

        // 1) 原名直查
        String photo = searchByName(key);

        // 2) 去掉音调后重试（João Pedro / Núñez / Højlund 这种）
        if (photo == null) {
            String ascii = stripAccents(key);
            if (!ascii.equals(key)) {
                photo = searchByName(ascii);
            }
        }

        // 3) 只留姓氏再试（"Mathis Cherki" → "Cherki"，handles 名字不完全一致的情况）
        if (photo == null) {
            String[] parts = key.split("\\s+");
            if (parts.length > 1) {
                String last = stripAccents(parts[parts.length - 1]);
                photo = searchByName(last);
            }
        }

        cache.put(key, photo == null ? "" : photo);
        return photo;
    }

    private String searchByName(String name) {
        try {
            String url = String.format(SEARCH_URL,
                    URLEncoder.encode(name, StandardCharsets.UTF_8));
            String body = http.getWithHeaders(url, Map.of(
                    "Origin", "https://www.premierleague.com",
                    "Referer", "https://www.premierleague.com/",
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            ));
            if (body == null || body.isEmpty()) return null;

            JsonNode root = mapper.readTree(body);
            JsonNode hits = root.path("hits").path("hit");
            if (!hits.isArray() || hits.size() == 0) {
                log.debug("[PlPhoto] no hits for {}", name);
                return null;
            }

            JsonNode picked = pickBestMatch(hits, name);
            String opta = picked.path("response").path("altIds").path("opta").asText(null);
            if (opta == null || opta.isBlank()) return null;

            log.debug("[PlPhoto] {} → {}", name, opta);
            return String.format(PHOTO_URL_TMPL, opta);
        } catch (Exception e) {
            log.debug("[PlPhoto] searchByName failed for {}: {}", name, e.getMessage());
            return null;
        }
    }

    /** "João" → "Joao"，"Højlund" → "Hojlund" */
    private static String stripAccents(String s) {
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "").replace("ø", "o").replace("Ø", "O");
    }

    private JsonNode pickBestMatch(JsonNode hits, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        for (JsonNode h : hits) {
            String display = h.path("response").path("name").path("display").asText("");
            if (display.equalsIgnoreCase(query)) return h;
        }
        for (JsonNode h : hits) {
            String display = h.path("response").path("name").path("display").asText("").toLowerCase(Locale.ROOT);
            if (display.contains(q) || q.contains(display)) return h;
        }
        return hits.get(0);
    }
}
