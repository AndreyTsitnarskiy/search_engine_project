package searchengine.services.service_impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.api_response.ApiResponse;
import searchengine.exceptions.SiteExceptions;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.SiteParser;
import searchengine.services.interfaces.IndexingService;
import searchengine.util.ConnectionUtil;
import searchengine.util.LemmaExecute;
import searchengine.util.PropertiesProject;
import searchengine.util.ReworkString;

import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private ReentrantLock lock = new ReentrantLock();

    @Getter
    private final PropertiesProject propertiesProject;
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
        deleteAllDataFromDatabase();
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
        log.info("INDEX PAGE: " + path);
        ApiResponse apiResponse = new ApiResponse();
        try {
            if (isPageBelongsToSiteSpecified(path)) {
                new Thread(() -> indexSinglePage(path)).start();
                log.info("Page indexed " + path);
                apiResponse.setResult(true);
            } else {
                apiResponse.setResult(false);
                apiResponse.setMessageError("Page is located outside the sites specified in the configuration file");
            }
        } catch (SiteExceptions siteExceptions) {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Path incorrect");
        }
        log.info("END INDEX PAGE OFF: " + path);
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if (isIndexing) {
            shutdown();
            apiResponse.setResult(true);
            log.info("Indexing stopped");
            saveDataFromMapsToDatabase();
            log.info("Data saved");
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

    public void indexSinglePage(String pageUrl) {
        SiteEntity siteEntity = findOrCreateNewSiteEntity(pageUrl);
        Connection connection = ConnectionUtil.getConnection(pageUrl, propertiesProject.getUserAgent(), propertiesProject.getReferrer());
        Connection.Response response = ConnectionUtil.getResponse(connection);
        Document document = ConnectionUtil.getDocument(connection);
        String pathToSave = ReworkString.getPathToSave(pageUrl, siteEntity.getUrl());
        int httpStatusCode = response.statusCode();

        PageEntity deletePageEntity = deleteOldPageEntity(pathToSave, siteEntity);
        String html = "";
        PageEntity pageEntity = new PageEntity(siteEntity, pathToSave, httpStatusCode, html);
        if (httpStatusCode != 200) {
            savePageAndSiteStatusTime(pageEntity, html, siteEntity);
        } else {
            html = document.outerHtml();
            if (deletePageEntity != null) {
                reduceLemmaFrequenciesByOnePage(html, siteEntity.getId());
            }
            savePageAndSiteStatusTime(pageEntity, html, siteEntity);
            log.info("Page indexed: " + pathToSave);
            extractLemmas(html, pageEntity, siteEntity);
        }
        fixSiteStatusAfterSinglePageIndexed(siteEntity);
    }

    private PageEntity deleteOldPageEntity(String path, SiteEntity siteEntity) {
        PageEntity deletePageEntity = pageRepository.findPageEntityByPathAndSite(path, siteEntity);
        if (deletePageEntity == null) {
            return null;
        }
        pageRepository.delete(deletePageEntity);
        return deletePageEntity;
    }

    private SiteEntity findOrCreateNewSiteEntity(String url) {
        String siteUrlFromPageUrl = ReworkString.getStartPage(url);
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(siteUrlFromPageUrl);
        if (siteEntity == null) {
            siteEntity = createSiteToHandleSinglePage(siteUrlFromPageUrl);
        }
        return siteEntity;
    }

    private void reduceLemmaFrequenciesByOnePage(String html, int siteId) {
        Map<String, Integer> allUniquePageLemmas = getAllLemmasPage(html);
        lemmaRepository.reduceByOneLemmaFrequencies(siteId, allUniquePageLemmas.keySet());
        lemmaRepository.deleteLemmasWithNoFrequencies(siteId);
    }

    private SiteEntity createSiteToHandleSinglePage(String siteHomePageToSave) {
        SiteEntity siteEntity = new SiteEntity();
        String currentSiteHomePage;
        for (Site site : sites.getSites()) {
            currentSiteHomePage = ReworkString.getStartPage(site.getUrl());
            if (siteHomePageToSave.equalsIgnoreCase(currentSiteHomePage)) {
                siteEntity = createAndPrepareSiteForIndexing(site);
                break;
            }
        }
        return siteEntity;
    }

    private void indexSingleSite(Site site) {
        try {
            SiteParser pageParse = initCollectionsForSiteAndCreateMainPageSiteParser(site);
            forkJoinPool.invoke(pageParse);
            fillLemmasAndIndexTable(site);
            markSiteAsIndexed(site);
            log.info("Indexing completed for " + site.getName());
        } catch (Exception exception) {
            log.warn("Indexing FAILED " + site.getName() + " due to " + exception);
            fixSiteIndexingError(site, exception);
            clearLemmasAndIndexTable(site);
        } finally {
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

    public void savePageAndSite(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        pageEntity.setContent(pageHtml);
        pageRepository.save(pageEntity);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    private Map<String, Integer> getAllLemmasPage(String html) {
        Document document = ConnectionUtil.parse(html);
        String title = document.title();
        String body = document.body().text();

        Map<String, Integer> titleLemmas = LemmaExecute.getLemmaMap(title);
        Map<String, Integer> bodyLemmas = LemmaExecute.getLemmaMap(body);

        return Stream.concat(titleLemmas.entrySet().stream(), bodyLemmas.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)));
    }

    public void extractLemmas(String html, PageEntity pageEntity, SiteEntity siteEntity) {
        Map<String, Integer> lemmaEntityHashMap = getAllLemmasPage(html);
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

    private void fillLemmasAndIndexTable(Site site) {
        String url = ReworkString.getStartPage(site.getUrl());
        int siteEntityId = siteRepository.findSiteEntityByUrl(url).getId();
        Map<String, LemmaEntity> lemmaEntityMap = lemmasMap.get(siteEntityId);
        Set<IndexEntity> indexEntitySet = indexMap.get(siteEntityId);
        lemmaRepository.saveAll(lemmaEntityMap.values());
        lemmasMap.get(siteEntityId).clear();
        indexRepository.saveAll(indexEntitySet);
        indexMap.get(siteEntityId).clear();
    }

    private void saveDataFromMapsToDatabase() {
        try {
            lock.lock();
            for (Site site : sites.getSites()) {
                fillLemmasAndIndexTable(site);
            }
        } catch (Exception exception) {
            log.warn("Data saving FAILED due to " + exception);
        } finally {
            lock.unlock();
        }
    }

    private void clearLemmasAndIndexTable(Site site) {
        String url = ReworkString.getStartPage(site.getUrl());
        int siteEntityId = siteRepository.findSiteEntityByUrl(url).getId();
        lemmasMap.get(siteEntityId).clear();
        indexMap.get(siteEntityId).clear();
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

    private void markIndexingCompletionIfApplicable() {
        List<SiteEntity> allSites = siteRepository.findAll();
        for (SiteEntity site : allSites) {
            if (site.getStatus().equals(Status.INDEXING)) {
                return;
            }
        }
        isIndexing = false;
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
            return propertiesProject.getInterruptedByUserMessage();
        } else if (e instanceof CertificateExpiredException || e instanceof SSLHandshakeException
                || e instanceof CertPathValidatorException) {
            return propertiesProject.getCertificateError();
        } else {
            return propertiesProject.getUnknownError() + " (" + e + ")";
        }
    }

    private boolean isPageBelongsToSiteSpecified(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) {
            return false;
        }
        List<Site> siteList = sites.getSites();
        for (Site site : siteList) {
            String siteHomePage = ReworkString.getStartPage(site.getUrl());
            String passedHomePage = ReworkString.getStartPage(pageUrl);
            if (passedHomePage.equalsIgnoreCase(siteHomePage)) {
                return true;
            }
        }
        return false;
    }

    private void fixSiteStatusAfterSinglePageIndexed(SiteEntity site) {
        site.setStatus(Status.INDEXED);
        siteRepository.save(site);
    }

    @Transactional
    private void deleteAllDataFromDatabase() {
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        indexRepository.deleteAll();
        siteRepository.deleteAll();
    }

    private void shutdown() {
        forkJoinPool.shutdownNow();
        try {
            forkJoinPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

