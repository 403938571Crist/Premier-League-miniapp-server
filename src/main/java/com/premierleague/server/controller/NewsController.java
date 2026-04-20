package com.premierleague.server.controller;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.dto.PageResult;
import com.premierleague.server.model.NewsArticle;
import com.premierleague.server.model.NewsListItem;
import com.premierleague.server.model.TransferNews;
import com.premierleague.server.service.NewsImageService;
import com.premierleague.server.service.NewsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资讯 Controller - 对应字段字典 API 设计
 * GET /api/news
 * GET /api/news/{id}
 * GET /api/news/transfers
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {
    
    private final NewsService newsService;
    private final NewsImageService newsImageService;
    
    public NewsController(NewsService newsService, NewsImageService newsImageService) {
        this.newsService = newsService;
        this.newsImageService = newsImageService;
    }
    
    /**
     * 获取资讯列表
     * GET /api/news?page=1&pageSize=10&sourceType=romano&tag=转会&keyword=热刺
     */
    @GetMapping
    public ApiResponse<PageResult<NewsListItem>> getNewsList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword
    ) {
        PageResult<NewsListItem> result = newsService.getNewsList(page, pageSize, sourceType, tag, keyword);
        return ApiResponse.ok(result);
    }
    
    /**
     * 获取资讯详情
     * GET /api/news/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<NewsArticle> getNewsDetail(@PathVariable String id) {
        return newsService.getNewsDetail(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.notFound("news"));
    }

    @GetMapping("/{id}/cover-preview")
    public ResponseEntity<byte[]> getNewsCoverPreview(@PathVariable String id) {
        return newsImageService.getCoverPreview(id);
    }
    
    /**
     * 获取转会快讯
     * GET /api/news/transfers?source=romano&teamId=73&playerId=123
     */
    @GetMapping("/transfers")
    public ApiResponse<List<TransferNews>> getTransferNews(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long playerId
    ) {
        List<TransferNews> result = newsService.getTransferNews(source, teamId, playerId);
        return ApiResponse.ok(result);
    }
}
