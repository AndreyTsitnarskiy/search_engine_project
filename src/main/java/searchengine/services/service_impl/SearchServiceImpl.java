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
        log.info("get query: " + url);
        List<SiteEntity> siteEntityList = new ArrayList<>();
        if (url == null || url.isEmpty()){
            log.info("true");
            siteEntityList = siteRepository.findAll();
        } else {
            log.info("false");
            SiteEntity siteEntity = getSiteEntityByUrl(url);
            siteEntityList.add(siteEntity);
        }
        return siteEntityList;
    }

    private Map<Float, PageEntity> getSearchResultAllSites(String query, String url) {
        List<SiteEntity> sites = getQueryFromCountSites(url);
        log.info("Get search result all sites count: " + sites.size());
        Map<Float, PageEntity> allResultsPage = findPagesByRelativePages(query);
        log.info("Get search result all sites pages: " + sites.size());
        Map<Float, PageEntity> result = new HashMap<>();
        for (SiteEntity siteEntity : sites) {
            for (Map.Entry<Float, PageEntity> entry : allResultsPage.entrySet()) {
                if (entry.getValue().getSite().getId() == siteEntity.getId()) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Map<Float, PageEntity> sortedMap = getSortedSearchResults(result);
        log.info("Get search result all sites pages after sorting: " + sites.size());
        return sortedMap;
    }

    private List<ApiSearchResult> getApiSearchResult(String query, String url){
        List<ApiSearchResult> apiSearchResultList = new ArrayList<>();
        Map<Float, PageEntity> result = getSearchResultAllSites(query, url);
        log.info("Get Api result undo: " + result.size());
        for (Map.Entry<Float, PageEntity> entry : result.entrySet()) {
            String snippet = execSnippet(query, entry.getValue());
            if(snippet == null || snippet.isEmpty()){
                continue;
            }
            ApiSearchResult apiSearchResult = new ApiSearchResult();

        }
        return apiSearchResultList;
    }

    private Map<Float, PageEntity> getSortedSearchResults(Map<Float, PageEntity> entry) {
        Map<Float, PageEntity> unsortedMap = new HashMap<>(entry);
        return  unsortedMap.entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
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
        if (pathToSave == null || pathToSave.isEmpty()){
            return null;
        }
        return pathToSave;
    }

    private boolean checkQuery(String query){
        return query == null && query.isEmpty() && query.matches("\\s+");
    }

    private boolean checkStatusSites(String url){
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
        if (siteEntity != null && siteEntity.getStatus().equals("INDEXED")){
            return true;
        }
        return false;
    }

    private SiteEntity getSiteEntityByUrl(String url){
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
        if (siteEntity != null){
            return siteEntity;
        }
        return null;
    }

    private List<LemmaEntity> getQueryWords(String query){
        Set<String> queryWords = LemmaExecute.getLemmaList(query);
        return lemmaRepository.findByLemmaName(queryWords);
    }

    private List<Integer> getListPageId(String query){
        Set<String> queryWords = LemmaExecute.getLemmaList(query);
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(queryWords);
        log.info("METHOD GET LIST PAGE ID FROM" + lemmaEntity.getLemma());
        return indexRepository.findPagesIdByLemmaIdIn(lemmaEntity.getId());
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
        Map<LemmaEntity, List<IndexEntity>> allLemmasAndValuePages = getAllIndexesForLemma(getQueryWords(query));
        List<Integer> listNumberPages = getListPageId(query);
        for (Integer pageId : listNumberPages) {
            float absolute = 0;
            for (Map.Entry<LemmaEntity, List<IndexEntity>> entry : allLemmasAndValuePages.entrySet()) {
                List<IndexEntity> indexes = entry.getValue();
                for (IndexEntity index : indexes) {
                    if (index.getPage().getId() == pageId) {
                        absolute += index.getRank_lemmas();
                    }
                }
            }
            result.put(pageId, absolute);
        }
        return result;
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

    private Map<Float, PageEntity> findPagesByRelativePages(String query) {
        Map<Integer, Float> data = calculateRelativePages(query);
        Map<Float, PageEntity> result = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : data.entrySet()) {
            result.put(entry.getValue(), pageRepository.findById(entry.getKey()).get());
        }
        return result;
    }
}
