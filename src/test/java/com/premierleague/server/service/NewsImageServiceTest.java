package com.premierleague.server.service;

import com.premierleague.server.repository.NewsRepository;
import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsImageServiceTest {

    private HttpClientUtil httpClientUtil;
    private NewsImageService newsImageService;

    @BeforeEach
    void setUp() {
        httpClientUtil = mock(HttpClientUtil.class);
        NewsRepository newsRepository = mock(NewsRepository.class);
        newsImageService = new NewsImageService(httpClientUtil, newsRepository);
        ReflectionTestUtils.setField(newsImageService, "publicBaseUrl", "http://localhost:8080");
    }

    @Test
    void resolvesTallCoverToPreviewEndpointAndKeepsOriginalForDetailImages() throws Exception {
        String tallUrl = "https://tu.duoduocdn.com/uploads/day_260420/tall.jpg";
        when(httpClientUtil.getBytes(tallUrl)).thenReturn(createTallImageBytes());

        String resolvedCover = newsImageService.resolveCoverImage("news-1", tallUrl);
        List<String> mergedImages = newsImageService.mergeDetailImages(tallUrl, List.of(
                "https://cdn.example.com/content-1.jpg",
                tallUrl
        ));

        assertEquals("http://localhost:8080/api/news/news-1/cover-preview", resolvedCover);
        assertEquals(List.of(
                tallUrl,
                "https://cdn.example.com/content-1.jpg"
        ), mergedImages);
    }

    @Test
    void buildsPreviewFromTopSectionOfTallImage() throws Exception {
        String tallUrl = "https://tu.duoduocdn.com/uploads/day_260420/tall-preview.jpg";
        when(httpClientUtil.getBytes(tallUrl)).thenReturn(createTallImageBytes());

        NewsImageService.PreviewAsset previewAsset = newsImageService.getOrCreatePreviewAsset(tallUrl);
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(previewAsset.bytes()));

        assertEquals(100, preview.getWidth());
        assertEquals(56, preview.getHeight());
        Color sampled = new Color(preview.getRGB(10, 10));
        assertTrue(sampled.getRed() > 200);
        assertTrue(sampled.getBlue() < 50);
    }

    private byte[] createTallImageBytes() throws Exception {
        BufferedImage image = new BufferedImage(100, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 100, 120);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 120, 100, 180);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
