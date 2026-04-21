package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.util.HttpClientUtil;
import com.premierleague.server.util.PlayerNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class WikipediaPlayerPhotoProvider {

    private static final String SUMMARY_URL =
            "https://en.wikipedia.org/api/rest_v1/page/summary/%s";
    private static final String WIKIDATA_SEARCH_URL =
            "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=%s&language=en&format=json"
                    + "&limit=5&type=item&origin=*";
    private static final String ENTITY_URL =
            "https://www.wikidata.org/wiki/Special:EntityData/%s.json";
    private static final String PAGE_IMAGE_URL =
            "https://en.wikipedia.org/w/api.php?action=query&prop=pageimages&piprop=thumbnail|original"
                    + "&pithumbsize=330&titles=%s&format=json&origin=*";
    private static final String COMMONS_CATEGORY_FILES_URL =
            "https://commons.wikimedia.org/w/api.php?action=query&list=categorymembers&cmtitle=%s"
                    + "&cmtype=file&cmlimit=10&format=json&origin=*";
    private static final String COMMONS_FILE_INFO_URL =
            "https://commons.wikimedia.org/w/api.php?action=query&titles=%s&prop=imageinfo&iiprop=url"
                    + "&format=json&origin=*";

    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String findPhotoUrl(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        String summaryPhoto = resolveSummaryPhoto(playerName);
        if (summaryPhoto != null) {
            return summaryPhoto;
        }

        String accentStrippedName = stripAccents(playerName);
        if (!accentStrippedName.equals(playerName)) {
            String normalizedSummaryPhoto = resolveSummaryPhoto(accentStrippedName);
            if (normalizedSummaryPhoto != null) {
                return normalizedSummaryPhoto;
            }
        }

        return resolveFromWikidata(playerName);
    }

    private String resolveSummaryPhoto(String playerName) {
        String title = encodeWikiTitle(playerName);
        String body = httpClientUtil.get(SUMMARY_URL.formatted(title));
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String original = root.path("originalimage").path("source").asText(null);
            if (hasText(original)) {
                return original;
            }

            String thumbnail = root.path("thumbnail").path("source").asText(null);
            if (hasText(thumbnail)) {
                return thumbnail;
            }
        } catch (Exception e) {
            log.debug("[WikipediaPhoto] failed to parse summary for {}: {}", playerName, e.getMessage());
        }

        return null;
    }

    private String resolveFromWikidata(String playerName) {
        try {
            JsonNode entity = loadBestEntity(playerName);
            if (entity == null) {
                return null;
            }

            String p18FileName = readClaimText(entity.path("claims"), "P18");
            if (hasText(p18FileName)) {
                String fileUrl = resolveCommonsFileUrl("File:" + p18FileName);
                if (fileUrl != null) {
                    return fileUrl;
                }
            }

            String enwikiTitle = entity.path("sitelinks").path("enwiki").path("title").asText(null);
            if (hasText(enwikiTitle)) {
                String pageImage = resolvePageImage(enwikiTitle);
                if (pageImage != null) {
                    return pageImage;
                }
            }

            String commonsCategory = entity.path("sitelinks").path("commonswiki").path("title").asText(null);
            if (hasText(commonsCategory)) {
                return resolveCommonsCategoryImage(commonsCategory);
            }
        } catch (Exception e) {
            log.debug("[WikipediaPhoto] failed to resolve {} from wikidata: {}", playerName, e.getMessage());
        }

        return null;
    }

    private JsonNode loadBestEntity(String playerName) throws Exception {
        String searchUrl = WIKIDATA_SEARCH_URL.formatted(URLEncoder.encode(playerName, StandardCharsets.UTF_8));
        String searchBody = httpClientUtil.get(searchUrl);
        if (searchBody == null || searchBody.isBlank()) {
            return null;
        }

        JsonNode searchRoot = objectMapper.readTree(searchBody);
        JsonNode matched = pickBestEntity(playerName, searchRoot.path("search"));
        if (matched == null) {
            return null;
        }

        String entityId = matched.path("id").asText(null);
        if (!hasText(entityId)) {
            return null;
        }

        String entityBody = httpClientUtil.get(ENTITY_URL.formatted(entityId));
        if (entityBody == null || entityBody.isBlank()) {
            return null;
        }

        JsonNode entityRoot = objectMapper.readTree(entityBody);
        return entityRoot.path("entities").path(entityId);
    }

    private JsonNode pickBestEntity(String playerName, JsonNode results) {
        if (!results.isArray()) {
            return null;
        }

        String queryKey = PlayerNameNormalizer.normalize(playerName);
        for (JsonNode result : results) {
            if (isFootballResult(result)
                    && queryKey.equals(PlayerNameNormalizer.normalize(result.path("label").asText("")))) {
                return result;
            }
        }

        for (JsonNode result : results) {
            if (isFootballResult(result)) {
                return result;
            }
        }

        return null;
    }

    private boolean isFootballResult(JsonNode result) {
        String description = result.path("description").asText("").toLowerCase(Locale.ROOT);
        return description.contains("football") || description.contains("soccer");
    }

    private String readClaimText(JsonNode claims, String propertyId) {
        JsonNode entries = claims.path(propertyId);
        if (!entries.isArray() || entries.isEmpty()) {
            return null;
        }

        JsonNode value = entries.get(0).path("mainsnak").path("datavalue").path("value");
        return value.isTextual() ? value.asText() : null;
    }

    private String resolvePageImage(String pageTitle) {
        String url = PAGE_IMAGE_URL.formatted(URLEncoder.encode(pageTitle, StandardCharsets.UTF_8));
        String body = httpClientUtil.get(url);
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode pages = root.path("query").path("pages");
            if (!pages.isObject()) {
                return null;
            }

            Iterator<JsonNode> iterator = pages.elements();
            while (iterator.hasNext()) {
                JsonNode page = iterator.next();
                String original = page.path("original").path("source").asText(null);
                if (hasText(original)) {
                    return original;
                }
                String thumbnail = page.path("thumbnail").path("source").asText(null);
                if (hasText(thumbnail)) {
                    return thumbnail;
                }
            }
        } catch (Exception e) {
            log.debug("[WikipediaPhoto] failed to parse page image for {}: {}", pageTitle, e.getMessage());
        }

        return null;
    }

    private String resolveCommonsCategoryImage(String categoryTitle) {
        String url = COMMONS_CATEGORY_FILES_URL.formatted(URLEncoder.encode(categoryTitle, StandardCharsets.UTF_8));
        String body = httpClientUtil.get(url);
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode members = root.path("query").path("categorymembers");
            if (!members.isArray() || members.isEmpty()) {
                return null;
            }

            for (JsonNode member : members) {
                String title = member.path("title").asText(null);
                if (hasText(title)) {
                    String fileUrl = resolveCommonsFileUrl(title);
                    if (fileUrl != null) {
                        return fileUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[WikipediaPhoto] failed to parse commons category {}: {}", categoryTitle, e.getMessage());
        }

        return null;
    }

    private String resolveCommonsFileUrl(String fileTitle) {
        String url = COMMONS_FILE_INFO_URL.formatted(URLEncoder.encode(fileTitle, StandardCharsets.UTF_8));
        String body = httpClientUtil.get(url);
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode pages = root.path("query").path("pages");
            if (!pages.isObject()) {
                return null;
            }

            Iterator<JsonNode> iterator = pages.elements();
            while (iterator.hasNext()) {
                JsonNode page = iterator.next();
                JsonNode imageInfo = page.path("imageinfo");
                if (imageInfo.isArray() && !imageInfo.isEmpty()) {
                    String directUrl = imageInfo.get(0).path("url").asText(null);
                    if (hasText(directUrl)) {
                        return directUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[WikipediaPhoto] failed to parse commons file {}: {}", fileTitle, e.getMessage());
        }

        return null;
    }

    private String encodeWikiTitle(String playerName) {
        String title = playerName.trim().replace(' ', '_');
        return URLEncoder.encode(title, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String stripAccents(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD);
        return normalized.replaceAll("\\p{M}", "")
                .replace('\u2019', '\'')
                .replace('\u2018', '\'');
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
