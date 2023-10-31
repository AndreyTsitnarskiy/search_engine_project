package searchengine.dto.api_search;

import lombok.Data;

@Data
public class ApiSearchResult {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
