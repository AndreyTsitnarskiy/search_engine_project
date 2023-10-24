package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import searchengine.dto.statistics.ApiResponse;


public interface IndexingService {

    ResponseEntity<ApiResponse> startIndexing();

    ResponseEntity<ApiResponse> indexPage(String page);

    ResponseEntity<ApiResponse> stopIndexing();

}
