package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.ApiIndexing;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final ParserSite parserSite;

    @Override
    public ResponseEntity<ApiIndexing> startIndexing() {
            parserSite.indexAllSites();
        return null;
    }

    @Override
    public ResponseEntity<ApiIndexing> indexPage(String path) {
        return null;
    }

    @Override
    public ResponseEntity<ApiIndexing> stopIndexing() {
        return null;
    }

}

