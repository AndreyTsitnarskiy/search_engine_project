package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.statistics.ApiIndexing;

import java.io.IOException;

public interface IndexingService {

    ResponseEntity<ApiIndexing> startIndexing() throws IOException;
    ResponseEntity<ApiIndexing> indexPage(String path) throws IOException;

    ResponseEntity<ApiIndexing> stopIndexing();

}
