package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.api_response.ApiResponse;
import searchengine.dto.api_search.ApiSearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.interfaces.IndexingService;
import searchengine.services.interfaces.SearchService;
import searchengine.services.interfaces.StatisticsService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @Autowired
    public ApiController(StatisticsService statisticsService, IndexingService indexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        return indexingService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        return indexingService.stopIndexing();
    }

    @PostMapping(value = "/indexPage", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ApiResponse> indexPage(@RequestParam(value = "url") String url) {
        return ResponseEntity.ok(indexingService.indexPage(URLDecoder.decode(url, StandardCharsets.UTF_8)).getBody());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiSearchResponse> search(@RequestParam(value = "query", required = false) String query,
                                                    @RequestParam(value = "url", required = false) String url,
                                                    @RequestParam(value = "offset", required = false) Integer offset,
                                                    @RequestParam(value = "limit", required = false) Integer limit) {
            return ResponseEntity.ok(searchService.search(query, url, offset, limit).getBody());
        }
    }
