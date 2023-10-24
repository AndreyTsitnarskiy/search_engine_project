package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ApiResponse;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.intetface.IndexingService;
import searchengine.util.ConnectionUtil;
import searchengine.util.Properties;
import searchengine.util.ReworkString;

import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private SiteParser siteParser;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final LemmaServiceImpl lemmaService;

    @Getter
    private final Properties properties;
    private volatile boolean isIndexing = false;

    @Getter
    private Set<String> webPages;

    @Getter
    private ConcurrentMap<String, Status> siteStatusMap;
    private ConcurrentMap<Integer, Map<String, LemmaEntity>> lemmasMap;
    private ConcurrentMap<Integer, Set<IndexEntity>> indexMap;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if (isIndexing) {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Indexing already started");
            log.info("Indexing already started");
        } else {
            new Thread(this::indexAll).start();
            apiResponse.setResult(true);
            log.info("Indexing started");
        }
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        ApiResponse apiResponse = new ApiResponse();
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if (isIndexing) {
            shutdown();
            apiResponse.setResult(true);
        } else {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Indexing not started");
        }
        return ResponseEntity.ok(apiResponse);
    }

    private void indexAll() {
        List<Site> allSiteConfig = sites.getSites();
        isIndexing = true;
        forkJoinPool = new ForkJoinPool();
        lemmasMap = new ConcurrentHashMap<>();
        indexMap = new ConcurrentHashMap<>();
        webPages = Collections.synchronizedSet(new HashSet<>());
        siteStatusMap = new ConcurrentHashMap<>();
        for (Site site : allSiteConfig) {
            Thread thread = new Thread(() -> indexSingleSite(site));
            thread.setName(site.getName());
            thread.start();
        }
    }

    private void indexSingleSite(Site site) {
        try {
            SiteParser pageParse = initCollectionsForSiteAndCreateMainPageSiteParser(site);
            forkJoinPool.invoke(pageParse);
            log.info("Indexing completed " + lemmasMap.size());
            fillLemmasAndIndexTable(site);
            log.info("Indexing completed " + site + " and size " + lemmasMap.size());
            markSiteAsIndexed(site);
        } catch (Exception exception) {
            log.warn("Indexing FAILED " + site.getName() + " due to " + exception);
            fixSiteIndexingError(site, exception);
            //clearLemmasAndIndexTable(site);
        } finally {
            //fillLemmasAndIndexTable(site);
            markIndexingCompletionIfApplicable();
        }
    }

    public void savePageAndSiteStatusTime(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        if (!forkJoinPool.isTerminating()
                && !forkJoinPool.isTerminated()
                && !siteStatusMap.get(siteEntity.getUrl()).equals(Status.FAILED)) {
            savePageAndSite(pageEntity, pageHtml, siteEntity);
        }
    }

    private void savePageAndSite(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        pageEntity.setContent(pageHtml);
        pageRepository.save(pageEntity);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    public void extractLemmas(String html, PageEntity pageEntity, SiteEntity siteEntity) {
        Map<String, Integer> lemmaEntityHashMap = getAllLemmasPage(html); //Леммы и частота их на странице
        for (String lemmas : lemmaEntityHashMap.keySet()) {
            Map<String, LemmaEntity> allLemmasBySiteId = lemmasMap.get(siteEntity.getId());
            LemmaEntity lemmaEntity = allLemmasBySiteId.get(lemmas);
            if (lemmaEntity == null) {
                lemmaEntity = new LemmaEntity();
                lemmaEntity.setLemma(lemmas);
                lemmaEntity.setFrequency(1);
                lemmaEntity.setSite(siteEntity);
                lemmasMap.get(siteEntity.getId()).put(lemmas, lemmaEntity);
            } else {
                int count = allLemmasBySiteId.get(lemmas).getFrequency();
                lemmasMap.get(siteEntity.getId()).get(lemmas).setFrequency(count + 1);
            }

            float lemmaRank = (float) lemmaEntityHashMap.get(lemmas);
            IndexEntity indexEntity = new IndexEntity(pageEntity, lemmaEntity, lemmaRank);
            indexMap.get(siteEntity.getId()).add(indexEntity);
        }
    }

    public void fillLemmasAndIndexTable(Site site) {
        String url = ReworkString.getStartPage(site.getUrl());
        int siteEntityId = siteRepository.findSiteEntityByUrl(url).getId();
        log.info("Fill lemmas " + site.getUrl());
        Map<String, LemmaEntity> lemmaEntityMap = lemmasMap.get(siteEntityId);
        Set<IndexEntity> indexEntitySet = indexMap.get(siteEntityId);
        log.info("Fill lemmas and index table for " + siteEntityId + " " + lemmaEntityMap.size());
        lemmaRepository.saveAll(lemmaEntityMap.values());
        lemmasMap.get(siteEntityId).clear();
        indexRepository.saveAll(indexEntitySet);
        indexMap.get(siteEntityId).clear();
    }

    public Map<String, Integer> getAllLemmasPage(String html) {
        Document document = ConnectionUtil.parse(html);
        String title = document.title();
        String body = document.body().text();

        Map<String, Integer> titleLemmas = lemmaService.getLemmaMap(title);
        Map<String, Integer> bodyLemmas = lemmaService.getLemmaMap(body);

        return Stream.concat(titleLemmas.entrySet().stream(), bodyLemmas.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)));
    }

    private void clearLemmasAndIndexTable(Site site) {
        String url = ReworkString.getStartPage(site.getUrl());
        int siteEntityId = siteRepository.findSiteEntityByUrl(url).getId();
        lemmasMap.get(siteEntityId).clear();
        indexMap.get(siteEntityId).clear();
    }

    private void markIndexingCompletionIfApplicable() {
        List<SiteEntity> allSites = siteRepository.findAll();
        for (SiteEntity site : allSites) {
            if (site.getStatus().equals(Status.INDEXING)) {
                return;
            }
        }
        isIndexing = false;
    }

    private SiteParser initCollectionsForSiteAndCreateMainPageSiteParser(Site siteToHandle) {
        SiteEntity siteEntity = createAndPrepareSiteForIndexing(siteToHandle);
        siteStatusMap.put(siteEntity.getUrl(), Status.INDEXING);
        Map<String, LemmaEntity> lemmaEntityMap = new HashMap<>();
        lemmasMap.put(siteEntity.getId(), lemmaEntityMap);
        Set<IndexEntity> indexEntitySet = new HashSet<>();
        indexMap.put(siteEntity.getId(), indexEntitySet);
        String siteHomePage = siteEntity.getUrl();
        webPages.add(siteHomePage);
        return new SiteParser(this, siteEntity, siteHomePage);
    }

    private SiteEntity createAndPrepareSiteForIndexing(Site site) {
        String homePage = ReworkString.getStartPage(site.getUrl());
        SiteEntity oldSiteEntity = siteRepository.findSiteEntityByUrl(homePage);
        if (oldSiteEntity != null) {
            oldSiteEntity.setStatus(Status.INDEXING);
            oldSiteEntity.setLocalDateTime(LocalDateTime.now());
            siteRepository.save(oldSiteEntity);
            siteRepository.deleteSiteEntityByUrl(homePage);
        }
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteEntity.setUrl(homePage);
        siteEntity.setName(site.getName());
        return siteRepository.save(siteEntity);
    }

    private void markSiteAsIndexed(Site site) {
        String homePage = ReworkString.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    private void fixSiteIndexingError(Site site, Exception e) {
        String error = getErrorMessage(e);
        String homePage = ReworkString.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    private String getErrorMessage(Exception e) {
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            return properties.getInterruptedByUserMessage();
        } else if (e instanceof CertificateExpiredException || e instanceof SSLHandshakeException
                || e instanceof CertPathValidatorException) {
            return properties.getCertificateError();
        } else {
            return properties.getUnknownError() + " (" + e + ")";
        }
    }

    private void fixSiteStatusAfterSinglePageIndexed(SiteEntity site) {
        site.setStatus(Status.INDEXED);
        siteRepository.save(site);
    }

    private void shutdown() {
        forkJoinPool.shutdownNow();
    }

}

