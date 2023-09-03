package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ApiResponse;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.intetface.IndexingService;
import searchengine.util.Properties;
import searchengine.util.ReworkString;

import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private SiteParserImp siteParserImp;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;

    @Getter
    private final Properties properties;
    private volatile boolean isIndexing = false;
    private ConcurrentMap<Integer, Set<IndexEntity>> indexEntityMapGropedBySiteId;

    @Getter
    private Set<String> webPages;

    @Getter
    private ConcurrentMap<String, Status> siteStatusMap;

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

    //метод сохраняет информацию о странице (pageEntity) и обновляет статус сайта (siteEntity).
    //Перед сохранением проверяется пул потоков forkJoinPool не завершается или не находится в процессе завершения.
    //статус сайта (Status) не установлен в FAILED.
    public void savePageAndSiteStatusTime(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        if (!forkJoinPool.isTerminating()
                && !forkJoinPool.isTerminated()
                && !siteStatusMap.get(siteEntity.getUrl()).equals(Status.FAILED)) {
            log.info("Page " + pageEntity.getSite().getUrl() + " saved method savePageAndSiteStatusTime " + pageEntity.getPath());
            savePageAndSite(pageEntity, pageHtml, siteEntity);
        }
    }

    //метод фактически сохраняет информацию о странице и обновляет статус сайта.
    //содержимое страницы (pageHtml) устанавливается в pageEntity.
    //затем pageEntity сохраняется в репозитории pageRepository.
    //siteEntity обновляется с новым значением LocalDateTime, и затем он сохраняется в репозитории siteRepository
    private void savePageAndSite(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        pageEntity.setContent(pageHtml);
        log.info("Page " + pageEntity.getSite().getUrl() + " saved method savePageAndSite " + pageEntity.getPath());
        pageRepository.save(pageEntity);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    //старт индексирования
    private void indexAll() {
        List<Site> allSiteConfig = sites.getSites();
        isIndexing = true;
        forkJoinPool = new ForkJoinPool();
        indexEntityMapGropedBySiteId = new ConcurrentHashMap<>();
        webPages = Collections.synchronizedSet(new HashSet<>());
        siteStatusMap = new ConcurrentHashMap<>();
        for (Site site : allSiteConfig) {
            Thread thread = new Thread(() -> indexSingleSite(site));
            thread.setName(site.getName());
            thread.start();
        }
    }

    //индексирование одной страницы
    private void indexSingleSite(Site site) {
        try {
            SiteParserImp pageCrawlerUnit = initCollectionsForSiteAndCreateMainPageCrawlerUnit(site);
            forkJoinPool.invoke(pageCrawlerUnit);
            markSiteAsIndexed(site);
            log.info("Indexing SUCCESS " + site.getName());
        } catch (Exception exception) {
            log.warn("Indexing FAILED " + site.getName() + " due to " + exception);
            fixSiteIndexingError(site, exception);
        } finally {
            markIndexingCompletionIfApplicable();
        }
    }

    //метод проверяет, завершилась ли индексация всех сайтов
    //если все сайты завершили индексацию, флаг isIndexing устанавливается в false
    private void markIndexingCompletionIfApplicable() {
        List<SiteEntity> allSites = siteRepository.findAll();
        for (SiteEntity site : allSites) {
            if (site.getStatus().equals(Status.INDEXING)) {
                return;
            }
        }
        isIndexing = false;
    }

    //метод инициализирует необходимые коллекции и создает экземпляр SiteParserImp для индексации главной страницы сайта
    //он также обновляет статус сайта на INDEXING
    private SiteParserImp initCollectionsForSiteAndCreateMainPageCrawlerUnit(Site siteToHandle) {
        SiteEntity siteEntity = createAndPrepareSiteForIndexing(siteToHandle);
        siteStatusMap.put(siteEntity.getUrl(), Status.INDEXING);
        Set<IndexEntity> indexEntitySet = new HashSet<>();
        indexEntityMapGropedBySiteId.put(siteEntity.getId(), indexEntitySet);
        String siteHomePage = siteEntity.getUrl();
        webPages.add(siteHomePage);
        return new SiteParserImp(this, siteEntity, siteHomePage);
    }

    //этот метод создает и подготавливает объект SiteEntity для индексации сайта
    //если сайт уже существует в базе данных, его статус обновляется на INDEXING
    //если сайта нет в базе данных, создается новый объект SiteEntity
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

    //метод помечает сайт как успешно проиндексированный, статус сайта обновляется на INDEXED.
    private void markSiteAsIndexed(Site site) {
        String homePage = ReworkString.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    //обрабатываем ошибку при индексации сайта, сайт помечается как неудачный, и ошибка сохраняется в базе данных
    private void fixSiteIndexingError(Site site, Exception e) {
        String error = getErrorMessage(e);
        String homePage = ReworkString.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    //генерируем текстовое сообщение об ошибке на основе переданного исключения
    //в сообщении указывается причина ошибки, и оно используется для сохранения информации об ошибке в базе данных
    private String getErrorMessage(Exception e) {
        log.info("Creating ERROR message for: " + e.toString());
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
        forkJoinPool.shutdown();
    }

}

