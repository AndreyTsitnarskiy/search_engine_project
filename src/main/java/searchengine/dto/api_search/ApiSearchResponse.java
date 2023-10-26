package searchengine.dto.api_search;

import lombok.Data;

import java.util.List;

@Data
public class ApiSearchResponse {
    private boolean result;
    private int count;
    private List<ApiSearchResult> data;
    private String messageError;
}
