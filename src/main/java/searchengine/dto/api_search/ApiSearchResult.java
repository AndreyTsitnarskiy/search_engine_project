package searchengine.dto.api_search;

import lombok.Data;

@Data
public class ApiSearchResult {
    private String url;
    private String title;
    private String snippet;
    private float relevance;
}
