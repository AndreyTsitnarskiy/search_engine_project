package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.ApiIndexing;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final ParserSiteImpl parserSite;

    @Override
    public ResponseEntity<ApiIndexing> startIndexing() throws IOException {
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

