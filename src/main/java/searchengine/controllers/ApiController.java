package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.ApiResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.interfaces.IndexingService;
import searchengine.services.interfaces.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    @Autowired
    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing(){
        return indexingService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing(){
        return indexingService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ApiResponse> indexPage(@RequestParam(name = "url", required = false) String url){
        return indexingService.indexPage(url);
    }
}
