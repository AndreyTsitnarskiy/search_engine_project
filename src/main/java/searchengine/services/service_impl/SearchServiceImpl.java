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
        ApiSearchResponse apiSearchResponse;
        if (url == null || url.isEmpty()){
            List<ApiSearchResult> allSitesResult = buildApiResultListAllSites(query);
            apiSearchResponse = buildApiSearchResponse(allSitesResult);
        } else {
            List<ApiSearchResult> singleSiteResult = buildApiResultListSingleSite(query, url);
            apiSearchResponse = buildApiSearchResponse(singleSiteResult);
        }
        return apiSearchResponse;
    }

    private Map<Float, PageEntity> getSearchResultAllSites(String query) {
        List<SiteEntity> sites = siteRepository.findAll();
        Map<Float, PageEntity> allResultsPage = new HashMap<>();
        for (SiteEntity siteEntity : sites) {
            allResultsPage.putAll(getSearchResults(query, siteEntity.getUrl()));
        }
        Map<Float, PageEntity> sortedMap = getSortedSearchResults(allResultsPage);
        return sortedMap;
    }

    private Map<Float, PageEntity> getSearchResults(String query, String url) {
        return findPagesByRelativePages(query, getSiteEntityByUrl(url));
    }

    private Map<Float, PageEntity> getSortedSearchResults(Map<Float, PageEntity> entry) {
        Map<Float, PageEntity> unsortedMap = new HashMap<>(entry);
        return  unsortedMap.entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private List<ApiSearchResult> buildApiResultListAllSites(String query) {
        List<ApiSearchResult> resultData = new ArrayList<>();
        Map<Float, PageEntity> allResultsPage = getSearchResultAllSites(query);
        for (Map.Entry<Float, PageEntity> entry : allResultsPage.entrySet()) {
            SiteEntity siteEntity = entry.getValue().getSite();
            String snippet = execSnippet(query, entry.getValue());
            if(snippet == null || snippet.isEmpty()){
                continue;
            }
            ApiSearchResult apiSearchResult = new ApiSearchResult();
            apiSearchResult.setSite(siteEntity.getUrl().substring(0, siteEntity.getUrl().length() - 1));
            apiSearchResult.setSiteName(siteEntity.getName());
            apiSearchResult.setUri(entry.getValue().getPath());
            apiSearchResult.setRelevance(entry.getKey());
            apiSearchResult.setTitle(execTitle(entry.getValue()));
            apiSearchResult.setSnippet(snippet);
            resultData.add(apiSearchResult);
        }
        return resultData;
    }

    private ApiSearchResponse buildApiSearchResponse(List<ApiSearchResult> entry) {
        ApiSearchResponse apiSearchResponse = new ApiSearchResponse();
        apiSearchResponse.setResult(true);
        List<ApiSearchResult> resultData = entry;
        apiSearchResponse.setCount(resultData.size());
        apiSearchResponse.setData(resultData);
        apiSearchResponse.setMessageError(null);
        return apiSearchResponse;
    }

    private List<ApiSearchResult> buildApiResultListSingleSite(String query, String url){
        List<ApiSearchResult> resultData = new ArrayList<>();
        Map<Float, PageEntity> sortedMap = getSortedSearchResults(getSearchResults(query, url));
        SiteEntity siteEntity = getSiteEntityByUrl(url);
        for (Map.Entry<Float, PageEntity> entry : sortedMap.entrySet()) {
            String snippet = execSnippet(query, entry.getValue());
            if(snippet == null || snippet.isEmpty()){
              continue;
            }
            ApiSearchResult apiSearchResult = new ApiSearchResult();
            apiSearchResult.setSite(siteEntity.getUrl().substring(0, siteEntity.getUrl().length() - 1));
            apiSearchResult.setSiteName(siteEntity.getName());
            apiSearchResult.setUri(entry.getValue().getPath());
            apiSearchResult.setRelevance(entry.getKey());
            apiSearchResult.setTitle(execTitle(entry.getValue()));
            apiSearchResult.setSnippet(snippet);
            resultData.add(apiSearchResult);
        }
        return resultData;
    }

    private String execTitle(PageEntity pageEntity){
        Document document = Jsoup.parse(pageEntity.getContent());
        return document.title();
    }

    private String execSnippet(String query, PageEntity pageEntity){
        Set<String> queryWords = LemmaExecute.getLemmaList(query);
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(pageEntity.getSite().getId(), queryWords);
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
        log.info("Method getSiteEntityByUrl " + siteEntity.getId() + " " + siteEntity.getName());
        if (siteEntity != null){
            return siteEntity;
        }
        return null;
    }

    private List<LemmaEntity> getQueryWords(String query, SiteEntity siteEntity){
        log.info("Method getQueryWords " + siteEntity.getId() + " " + siteEntity.getName());
        Set<String> queryWords = LemmaExecute.getLemmaList(query);
        return lemmaRepository.findByLemmaName(siteEntity.getId(), queryWords);
    }

    private List<Integer> getListPageId(String query, SiteEntity siteEntity){
        Set<String> queryWords = LemmaExecute.getLemmaList(query);
        log.info("queryWords: " + queryWords + " query: " + queryWords.size());
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(siteEntity.getId(), queryWords);
        return indexRepository.findPagesIdByLemmaIdIn(lemmaEntity.getId());
    }

    private Map<LemmaEntity, List<IndexEntity>> getAllIndexesForLemma(List<LemmaEntity> queryWords, SiteEntity siteEntity){
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

    private Map<Integer, Float> calculateAbsolutePages(String query, SiteEntity siteEntity) {
        Map<Integer, Float> result = new HashMap<>();
        Map<LemmaEntity, List<IndexEntity>> allLemmasAndValuePages = getAllIndexesForLemma(getQueryWords(query, siteEntity), siteEntity);
        List<Integer> listNumberPages = getListPageId(query, siteEntity);

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

    private Map<Integer, Float> calculateRelativePages(String query, SiteEntity siteEntity){
        float maxAbsoluteValue = findMaxValue(calculateAbsolutePages(query, siteEntity));
        Map<Integer, Float> result = new HashMap<>();
        Map<Integer, Float> calcAbsolutePages = calculateAbsolutePages(query, siteEntity);
        for (Map.Entry<Integer, Float> entry : calcAbsolutePages.entrySet()) {
            result.put(entry.getKey(), entry.getValue() / maxAbsoluteValue);
        }
        return result;
    }

    private Map<Float, PageEntity> findPagesByRelativePages(String query, SiteEntity siteEntity) {
        Map<Integer, Float> data = calculateRelativePages(query, siteEntity);
        Map<Float, PageEntity> result = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : data.entrySet()) {
            result.put(entry.getValue(), pageRepository.findById(entry.getKey()).get());
        }
        return result;
    }
}
