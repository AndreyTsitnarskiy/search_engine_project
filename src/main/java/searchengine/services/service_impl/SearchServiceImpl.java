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
import searchengine.services.interfaces.LemmaService;
import searchengine.services.interfaces.SearchService;
import searchengine.util.KMPSnippet;

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
    private final LemmaService lemmaService;

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

    private ApiSearchResponse getApiSearchResponse(String query, String url){
        ApiSearchResponse apiSearchResponse = new ApiSearchResponse();
        apiSearchResponse.setResult(true);
        List<ApiSearchResult> resultData = search(query, url);
        apiSearchResponse.setCount(resultData.size());
        apiSearchResponse.setData(resultData);
        apiSearchResponse.setMessageError(null);
        return apiSearchResponse;
    }

    private List<ApiSearchResult> search(String query, String url){
        List<ApiSearchResult> resultData = new ArrayList<>();
        Map<Float, PageEntity> execData = findPagesByRelativePages(query, getSiteEntityByUrl(url));
        Map<Float, PageEntity> sortedMap = execData.entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        SiteEntity siteEntity = getSiteEntityByUrl(url);
        for (Map.Entry<Float, PageEntity> entry : sortedMap.entrySet()) {
            ApiSearchResult apiSearchResult = new ApiSearchResult();
            apiSearchResult.setSite(siteEntity.getUrl().substring(0, siteEntity.getUrl().length() - 1));
            apiSearchResult.setSiteName(siteEntity.getName());
            apiSearchResult.setUri(entry.getValue().getPath());
            apiSearchResult.setRelevance(entry.getKey());
            apiSearchResult.setTitle(execTitle(entry.getValue()));
            apiSearchResult.setSnippet(execSnippet(query, getSiteEntityByUrl(url), entry.getValue()));
            resultData.add(apiSearchResult);
        }
        return resultData;
    }

    private String execTitle(PageEntity pageEntity){
        Document document = Jsoup.parse(pageEntity.getContent());
        return document.title();
    }

    private String execSnippet(String query, SiteEntity siteEntity, PageEntity pageEntity){
        Set<String> queryWords = lemmaService.getLemmaList(query);
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(siteEntity.getId(), queryWords);
        return kmpSnippet.cutSnippet(pageEntity.getContent(), lemmaEntity.getLemma());
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

    private List<LemmaEntity> getQueryWords(String query, SiteEntity siteEntity){
        Set<String> queryWords = lemmaService.getLemmaList(query);
        return lemmaRepository.findByLemmaName(siteEntity.getId(), queryWords);
    }

    private List<Integer> getListPageId(String query, SiteEntity siteEntity){
        Set<String> queryWords = lemmaService.getLemmaList(query);
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
