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
        if(checkQuery(query) || getLemmaEntityList(query).isEmpty()){
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

    private List<ApiSearchResult> getApiSearchResult(String query, String url){
        List<ApiSearchResult> apiSearchResultList = new ArrayList<>();
        Map<PageEntity, Float> result = sortPagesByRelationsValue(query, url);
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

    private String execSnippet(String query, PageEntity pageEntity){
        List<LemmaEntity> queryWords = getLemmaEntityList(query);
        List<String> lemmasName = queryWords.stream().map(LemmaEntity::getLemma).collect(Collectors.toList());
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(lemmasName);
        String pathToSave = kmpSnippet.idleSnippet(pageEntity.getContent(), lemmaEntity.getLemma());
        for (LemmaEntity word : queryWords) {
            pathToSave = kmpSnippet.snippetFinishResult(pathToSave, word.getLemma());
        }
        if (pathToSave == null || pathToSave.isEmpty()){
            return null;
        }
        return pathToSave;
    }

    private String execTitle(PageEntity pageEntity){
        Document document = Jsoup.parse(pageEntity.getContent());
        return document.title();
    }

    private Map<PageEntity, Float> sortPagesByRelationsValue(String query, String url){
        Map<PageEntity, Float> map = getPagesByRelationsRanking(query, url);
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
    }

    private Map<PageEntity, Float> getPagesByRelationsRanking(String query, String url){
        Map<PageEntity, Float> map = new HashMap<>();
        Map<IndexEntity, Float> relativeResultRanking = getRelativeResultRanking(query, url);
        for (Map.Entry<IndexEntity, Float> entry : relativeResultRanking.entrySet()) {
            map.put(entry.getKey().getPage(), entry.getValue());
        }
        return map;

    }

    private Map<IndexEntity, Float> getRelativeResultRanking(String query, String url){
        Map<IndexEntity, Float> map = getCalculateAbsoluteRanking(query, url);
        float max = getMaxValueAbsoluteRanking(map);
        for (Map.Entry<IndexEntity, Float> entry : map.entrySet()) {
            entry.setValue(entry.getValue() / max);
        }
        return map;
    }

    private float getMaxValueAbsoluteRanking(Map<IndexEntity, Float> map){
        float max = 0;
        for (Map.Entry<IndexEntity, Float> entry : map.entrySet()) {
            if(entry.getValue() > max){
                max = entry.getValue();
            }
        }
        return max;
    }

    private Map<IndexEntity, Float> getCalculateAbsoluteRanking(String query, String url){
        Map<IndexEntity, Float> map = new HashMap<>();
        Map<LemmaEntity, List<IndexEntity>> indexesForLemmaByPageId = getAllOfIndexesForLemmaByPageId(query, url);
        for (Map.Entry<LemmaEntity, List<IndexEntity>> entry : indexesForLemmaByPageId.entrySet()) {
            for (IndexEntity indexEntity : entry.getValue()){
                if(map.containsKey(indexEntity)){
                    map.put(indexEntity, map.get(indexEntity.getRank_lemmas()) + indexEntity.getRank_lemmas());
                }
                map.put(indexEntity, indexEntity.getRank_lemmas());
            }
        }
        return map;
    }

    private Map<LemmaEntity, List<IndexEntity>> getAllOfIndexesForLemmaByPageId(String query, String url){
        Map<LemmaEntity, List<IndexEntity>> map = new HashMap<>();
        List<Integer> pageIdList = getPageIdListFromIndexRepo(query, url);
        log.info("Size page: " + pageIdList.size() + " and " + pageIdList);
        List<LemmaEntity> lemmaEntityList = actualizeLemmaEntityList(query, url);
        for (LemmaEntity lemmaEntity : lemmaEntityList){
            long lemmaId = lemmaEntity.getId();
            map.put(lemmaEntity, indexRepository.findPagesIdByLemmaIdInIsPageList(lemmaId, pageIdList));
        }
        return map;
    }

    private List<Integer> getPageIdListFromIndexRepo(String query, String url){
        LemmaEntity lemmaEntity = getRelativeFrequency(query, url);
        return indexRepository.findPagesIdByLemmaIdIn(lemmaEntity.getId());
    }

    private LemmaEntity getRelativeFrequency(String query, String url){
        LemmaEntity lemmaEntity;
        List<LemmaEntity> lemmaEntityList = actualizeLemmaEntityList(query, url);
        List<String> lemmasName = lemmaEntityList.stream().map(LemmaEntity::getLemma).distinct().collect(Collectors.toList());
        lemmaEntity = lemmaRepository.findByMinFrequency(lemmasName);
        return lemmaEntity;
    }

    private List<LemmaEntity> actualizeLemmaEntityList(String query, String url){
        List<LemmaEntity> lemmaEntityList = getLemmaEntityList(query);
        List<SiteEntity> siteEntityList = getSiteEntityList(url);
        List<Integer> siteIdList = siteEntityList.stream().map(SiteEntity::getId).collect(Collectors.toList());
        List<String> lemmaNameList = lemmaEntityList.stream().map(LemmaEntity::getLemma).collect(Collectors.toList());
        return lemmaRepository.findAllByLemmaNameAndSiteName(lemmaNameList, siteIdList);
    }

    private List<LemmaEntity> getLemmaEntityList(String query){
        log.info("line 189 query: {}", query);
        List<LemmaEntity> lemmaEntityList;
        Set<String> lemmaList = LemmaExecute.getLemmaList(query);
        log.info("line 192 lemmaList: {}", lemmaList);
        lemmaEntityList = lemmaRepository.findAllByLemmaName(lemmaList);
        return lemmaEntityList;
    }

    private List<SiteEntity> getSiteEntityList(String url){
        List<SiteEntity> siteEntityList = new ArrayList<>();
        if (url == null || url.isEmpty()){
            siteEntityList = siteRepository.findAll();
        } else {
            SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
            if (siteEntity != null){
                siteEntityList.add(siteEntity);
            }
        }
        return siteEntityList;
    }

    private boolean checkStatusSites(String url){
        List<SiteEntity> siteEntityList = getSiteEntityList(url);
        return siteEntityList.isEmpty();
    }

    private boolean checkQuery(String query){
        return query == null || query.isEmpty();
    }
}