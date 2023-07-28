package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ApiResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.util.ConnectionUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private SitesList sites;
    private final SiteParser siteParser;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private PageRepository pageRepository;
    private SiteRepository siteRepository;
    private volatile boolean isIndexing = false;

    @Autowired
    public IndexingServiceImpl(SitesList sites, SiteParser siteParser, PageRepository pageRepository, SiteRepository siteRepository) {
        this.sites = sites;
        this.siteParser = siteParser;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if (isIndexing) {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Indexing already started");
        } else {
            new Thread(this::indexAll).start();
            apiResponse.setResult(true);
        }
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        return null;
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if (isIndexing) {
            shutdown();
            apiResponse.setResult(true);
        } else {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Indexing is not started");
        }
        return ResponseEntity.ok(apiResponse);
    }

    private void addPageToDB(String path, SiteEntity site) throws IOException {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(path);
        pageEntity.setSiteId(site.getId());
        pageEntity.setCode(ConnectionUtil.httpResponseCode(path));
        pageEntity.setContent(ConnectionUtil.content(path));
        pageRepository.save(pageEntity);
    }

    private void addSiteToDB(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(Status.INDEXED);
        siteEntity.setLastError(null);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    private void indexAll() {
        isIndexing = true;
        ForkJoinPool pool = new ForkJoinPool();
        List<Site> siteList = sites.getSites();
        for (Site site : siteList) {
            addSiteToDB(site);
            pool.invoke(new SiteParserImpl(site.getUrl()));
        }
        shutdown();
        log.info("Indexing finished");
    }

    private void shutdown() {
        forkJoinPool.shutdown();
    }

}

