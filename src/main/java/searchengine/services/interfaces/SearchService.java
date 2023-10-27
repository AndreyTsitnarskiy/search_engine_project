package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import searchengine.dto.api_search.ApiSearchResponse;

public interface SearchService {
    ResponseEntity<ApiSearchResponse> search(String query, String url, int offset, int limit);
}
