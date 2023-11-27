package searchengine.services.service_impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.api_search.ApiSearchResponse;
import searchengine.dto.api_search.ApiSearchResult;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.SearchService;
import searchengine.util.KMPSnippet;
import searchengine.util.LemmaExecute;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final KMPSnippet kmpSnippet;

    @Override
    public ResponseEntity<ApiSearchResponse> search(String query, String url, int offset, int limit) {
        log.info("Received search request with query: {}, url: {}, offset: {}, limit: {}", query, url, offset, limit);
        ApiSearchResponse apiSearchResponse = new ApiSearchResponse();
        apiSearchResponse.setResult(false);
        if(checkQuery(query)){
            apiSearchResponse.setMessageError("Query is incorrect or empty");
        } else if (checkStatusSites(url)){
            apiSearchResponse.setMessageError("Page is located outside the sites specified in the configuration file");
        } else {
            apiSearchResponse = getApiSearchResponse(query, url);
        }
        return ResponseEntity.ok(apiSearchResponse);
    }

    private ApiSearchResponse getApiSearchResponse(String query, String url) {
        ApiSearchResponse apiSearchResponse = new ApiSearchResponse();
        apiSearchResponse.setResult(true);
        List<ApiSearchResult> resultData = getApiSearchResult(query, url);
        apiSearchResponse.setCount(resultData.size());
        apiSearchResponse.setData(resultData);
        apiSearchResponse.setMessageError(null);
        return apiSearchResponse;
    }

    private List<SiteEntity> getQueryFromCountSites(String url){
        List<SiteEntity> siteEntityList = new ArrayList<>();
        if (url == null || url.isEmpty()){
            siteEntityList = siteRepository.findAll();
        } else {
            SiteEntity siteEntity = getSiteEntityByUrl(url);
            siteEntityList.add(siteEntity);
        }
        return siteEntityList;
    }

    private Map<PageEntity, Float> getSearchResultAllSites(String query, String url) {
        List<SiteEntity> sites = getQueryFromCountSites(url);
        Map<PageEntity, Float> allResultsPage = findPagesByRelativePages(query);
        log.info("Найдено страниц: " + allResultsPage.size());
        return filterResultsBySites(allResultsPage, sites);
    }

    private Map<PageEntity, Float> filterResultsBySites(Map<PageEntity, Float> allResultsPage, List<SiteEntity> sites) {
        return allResultsPage.entrySet()
                .stream()
                .filter(entry -> sites.stream().anyMatch(site -> entry.getKey().getSite().getId() == (site.getId())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    private List<ApiSearchResult> getApiSearchResult(String query, String url){
        List<ApiSearchResult> apiSearchResultList = new ArrayList<>();
        Map<PageEntity, Float> result = getSortedSearchResults(getSearchResultAllSites(query, url));
        log.info("Количество страниц найденых по поиску: " + result.size());
        for (Map.Entry<PageEntity, Float> entry : result.entrySet()) {
            String snippet = execSnippet(query, entry.getKey());
            if(snippet == null || snippet.isEmpty()){
                continue;
            }
            ApiSearchResult apiSearchResult = new ApiSearchResult();
            apiSearchResult.setSiteName(entry.getKey().getSite().getName());
            apiSearchResult.setSite(entry.getKey().getSite().getUrl());
            apiSearchResult.setUri(entry.getKey().getPath());
            apiSearchResult.setSnippet(snippet);
            apiSearchResult.setTitle(execTitle(entry.getKey()));
            apiSearchResult.setRelevance(entry.getValue());
            apiSearchResultList.add(apiSearchResult);
        }
        return apiSearchResultList;
    }

    // Сортировка результатов поиска
    private Map<PageEntity, Float> getSortedSearchResults(Map<PageEntity, Float> entry) {
        return entry.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private String execTitle(PageEntity pageEntity){
        Document document = Jsoup.parse(pageEntity.getContent());
        return document.title();
    }

    private String execSnippet(String query, PageEntity pageEntity){
        Set<String> queryWords = LemmaExecute.getLemmaList(query);
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(queryWords);
        String pathToSave = kmpSnippet.idleSnippet(pageEntity.getContent(), lemmaEntity.getLemma());
        String finishSnippet = kmpSnippet.snippetFinishResult(pathToSave, queryWords);
        if (finishSnippet == null || finishSnippet.isEmpty()){
            return null;
        }
        return finishSnippet;
    }

    private boolean checkQuery(String query){
        return query == null || query.isEmpty() || query.matches("\\s+");
    }

    private boolean checkStatusSites(String url){
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
        if (siteEntity == null){
            return false;
        } else if (siteEntity.getStatus().equals("INDEXED")){
            return false;
        }
        return true;
    }

    private SiteEntity getSiteEntityByUrl(String url){
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
        if (siteEntity != null){
            return siteEntity;
        }
        return null;
    }

    //Берем все леммы
    private List<LemmaEntity> getQueryWords(String query){
        Set<String> queryWords = LemmaExecute.getLemmaList(query);
        log.info("Леммы запроса метод getQueryWords: " + queryWords);
        return lemmaRepository.findByLemmaName(queryWords);
    }

    private List<Integer> getListPageIdFromIndexRepository(String query){
        List<Integer> listPageIdFromLemmaMinFrequency = new ArrayList<>();
        Set<String> queryWords = LemmaExecute.getLemmaList(query);
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(queryWords);
        List<LemmaEntity> lemmaEntitiesAllInRepository = lemmaRepository.getLemmaAllSites(lemmaEntity.getLemma());
        for (LemmaEntity lemma : lemmaEntitiesAllInRepository) {
            listPageIdFromLemmaMinFrequency.addAll(indexRepository.findPagesIdByLemmaIdIn(lemma.getId()));
        }
        return listPageIdFromLemmaMinFrequency;
    }

    private Map<LemmaEntity, List<IndexEntity>> getAllIndexesForLemma(List<LemmaEntity> queryWords){
        Map<LemmaEntity, List<IndexEntity>> result = new HashMap<>();
        for (LemmaEntity lemmaEntity : queryWords) {
            long lemmaId = lemmaEntity.getId();
            List<Integer> listPageId = indexRepository.findPagesIdByLemmaIdIn(lemmaId);
            List<IndexEntity> indexEntities = indexRepository.findPagesIdByLemmaIdInIsPageList(lemmaEntity.getId(),
                    listPageId);
            result.put(lemmaEntity, indexEntities);
        }
        return result;
    }

    private Map<Integer, Float> calculateAbsolutePages(String query) {
        Map<Integer, Float> result = new HashMap<>();
        List<LemmaEntity> allWordsInQuery = getQueryWords(query);
        Map<LemmaEntity, List<IndexEntity>> allLemmasAndValuePages = getAllIndexesForLemma(allWordsInQuery);
        List<Integer> listNumberPages = getListPageIdFromIndexRepository(query);
        for (Integer pageId : listNumberPages) {
            float absolute = calculateAbsoluteForPage(pageId, allLemmasAndValuePages);
            result.put(pageId, absolute);
        }

        return result;
    }

    private float calculateAbsoluteForPage(Integer pageId, Map<LemmaEntity, List<IndexEntity>> allLemmasAndValuePages) {
        float absolute = 0;
        for (Map.Entry<LemmaEntity, List<IndexEntity>> entry : allLemmasAndValuePages.entrySet()) {
            absolute += calculateAbsoluteForLemmaAndPage(entry.getKey(), entry.getValue(), pageId);
        }
        return absolute;
    }

    private float calculateAbsoluteForLemmaAndPage(LemmaEntity lemmaEntity, List<IndexEntity> indexes, Integer pageId) {
        return indexes.stream()
                .filter(index -> index.getPage().getId() == pageId)
                .map(IndexEntity::getRank_lemmas)
                .reduce(Float::sum)
                .orElse(0f);
    }

    private float findMaxValue(Map<Integer, Float> absolutePages){
        float maxAbsoluteValue = Float.MIN_VALUE;
        for (Map.Entry<Integer, Float> entry : absolutePages.entrySet()) {
            if(entry.getValue() > maxAbsoluteValue){
                maxAbsoluteValue = entry.getValue();
            }
        }
        return maxAbsoluteValue;
    }

    private Map<Integer, Float> calculateRelativePages(String query){
        float maxAbsoluteValue = findMaxValue(calculateAbsolutePages(query));
        Map<Integer, Float> result = new HashMap<>();
        Map<Integer, Float> calcAbsolutePages = calculateAbsolutePages(query);
        for (Map.Entry<Integer, Float> entry : calcAbsolutePages.entrySet()) {
            result.put(entry.getKey(), entry.getValue() / maxAbsoluteValue);
        }
        return result;
    }

    private Map<PageEntity, Float> findPagesByRelativePages(String query) {
        Map<Integer, Float> data = calculateRelativePages(query);
        Map<PageEntity, Float> result = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : data.entrySet()) {
            result.put(pageRepository.findById(entry.getKey()).get(), entry.getValue());
        }
        return result;
    }
}
