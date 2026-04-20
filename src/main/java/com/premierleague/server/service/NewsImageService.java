package com.premierleague.server.service;

import com.premierleague.server.entity.News;
import com.premierleague.server.repository.NewsRepository;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsImageService {

    private static final double TALL_IMAGE_THRESHOLD = 1.4d;
    private static final double PREVIEW_HEIGHT_RATIO = 0.56d;

    private final HttpClientUtil httpClientUtil;
    private final NewsRepository newsRepository;

    private final ConcurrentMap<String, ImageAnalysis> imageAnalysisCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PreviewAsset> previewAssetCache = new ConcurrentHashMap<>();

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public String resolveCoverImage(String newsId, String originalCoverImage) {
        if (!isTallImage(originalCoverImage)) {
            return originalCoverImage;
        }
        return buildPreviewUrl(newsId);
    }

    public List<String> mergeDetailImages(String originalCoverImage, List<String> contentImages) {
        if (!isTallImage(originalCoverImage)) {
            return contentImages;
        }

        List<String> merged = new ArrayList<>();
        if (originalCoverImage != null && !originalCoverImage.isBlank()) {
            merged.add(originalCoverImage);
        }
        for (String image : contentImages) {
            if (!sameImage(originalCoverImage, image)) {
                merged.add(image);
            }
        }
        return merged;
    }

    public ResponseEntity<byte[]> getCoverPreview(String newsId) {
        Optional<News> newsOpt = newsRepository.findById(newsId);
        if (newsOpt.isEmpty() || newsOpt.get().getCoverImage() == null || newsOpt.get().getCoverImage().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        String originalCoverImage = newsOpt.get().getCoverImage();
        PreviewAsset previewAsset = getOrCreatePreviewAsset(originalCoverImage);
        if (previewAsset == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(originalCoverImage))
                    .build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic())
                .header(HttpHeaders.CONTENT_TYPE, previewAsset.contentType())
                .body(previewAsset.bytes());
    }

    PreviewAsset getOrCreatePreviewAsset(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        PreviewAsset cached = previewAssetCache.get(imageUrl);
        if (cached != null) {
            return cached;
        }

        PreviewAsset created = buildPreviewAsset(imageUrl);
        if (created != null) {
            previewAssetCache.putIfAbsent(imageUrl, created);
        }
        return created;
    }

    boolean isTallImage(String imageUrl) {
        return getImageAnalysis(imageUrl).tall();
    }

    private String buildPreviewUrl(String newsId) {
        String normalizedBase = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return normalizedBase + "/api/news/" + newsId + "/cover-preview";
    }

    private ImageAnalysis getImageAnalysis(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return ImageAnalysis.empty();
        }

        ImageAnalysis cached = imageAnalysisCache.get(imageUrl);
        if (cached != null) {
            return cached;
        }

        ImageAnalysis created = inspectImage(imageUrl);
        imageAnalysisCache.putIfAbsent(imageUrl, created);
        return created;
    }

    private ImageAnalysis inspectImage(String imageUrl) {
        try {
            byte[] bytes = httpClientUtil.getBytes(imageUrl);
            if (bytes == null || bytes.length == 0) {
                return ImageAnalysis.empty();
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return ImageAnalysis.empty();
            }

            double ratio = (double) image.getHeight() / image.getWidth();
            return new ImageAnalysis(image.getWidth(), image.getHeight(), ratio >= TALL_IMAGE_THRESHOLD);
        } catch (Exception e) {
            log.debug("[NewsImage] Failed to inspect image {}: {}", imageUrl, e.getMessage());
            return ImageAnalysis.empty();
        }
    }

    private PreviewAsset buildPreviewAsset(String imageUrl) {
        try {
            byte[] bytes = httpClientUtil.getBytes(imageUrl);
            if (bytes == null || bytes.length == 0) {
                return null;
            }

            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
                return null;
            }

            int previewHeight = Math.min(source.getHeight(),
                    Math.max(1, (int) Math.round(source.getWidth() * PREVIEW_HEIGHT_RATIO)));
            BufferedImage cropped = source.getSubimage(0, 0, source.getWidth(), previewHeight);
            BufferedImage encoded = new BufferedImage(cropped.getWidth(), cropped.getHeight(), BufferedImage.TYPE_INT_RGB);
            encoded.getGraphics().drawImage(cropped, 0, 0, null);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(encoded, "jpg", output);
            return new PreviewAsset(output.toByteArray(), MediaType.IMAGE_JPEG_VALUE);
        } catch (Exception e) {
            log.warn("[NewsImage] Failed to build preview for {}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    private boolean sameImage(String left, String right) {
        return imageIdentity(left).equals(imageIdentity(right));
    }

    private String imageIdentity(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return "";
        }

        String normalized = imageUrl;
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private record ImageAnalysis(int width, int height, boolean tall) {
        private static ImageAnalysis empty() {
            return new ImageAnalysis(0, 0, false);
        }
    }

    record PreviewAsset(byte[] bytes, String contentType) {
    }
}
