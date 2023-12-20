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
    private List<String> cachedLemmasToString;
    private List<LemmaEntity> cashedLemmasToLemmaEntityList = new ArrayList<>();

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
            buildStringListAndLemmaEntityList(query);
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
        log.info("result map pages: " +result.size());
        for (Map.Entry<PageEntity, Float> entry : result.entrySet()) {
            String snippet = execSnippet(entry.getKey());
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

    private String execSnippet(PageEntity pageEntity){
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(cachedLemmasToString);
        String pathToSave = kmpSnippet.idleSnippet(pageEntity.getContent(), lemmaEntity.getLemma());
        for (LemmaEntity word : cashedLemmasToLemmaEntityList) {
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
        Map<LemmaEntity, List<IndexEntity>> indexesForLemmaByPageId = getAllOfIndexesForLemmaByPageId(url);
        for (Map.Entry<LemmaEntity, List<IndexEntity>> entry : indexesForLemmaByPageId.entrySet()) {
            log.info("size list index in lemma entity key: " + entry.getValue().size());
            for (IndexEntity indexEntity : entry.getValue()){
                if(map.containsKey(indexEntity)){
                    map.put(indexEntity, map.get(indexEntity.getRank_lemmas()) + indexEntity.getRank_lemmas());
                }
                map.put(indexEntity, indexEntity.getRank_lemmas());
            }
        }
        return map;
    }

    private Map<LemmaEntity, List<IndexEntity>> getAllOfIndexesForLemmaByPageId(String url){
        Map<LemmaEntity, List<IndexEntity>> map = new HashMap<>();
        List<Integer> pageIdList = getPageIdListFromIndexRepo();
        log.info("Size page: " + pageIdList.size() + " and " + pageIdList);
        List<LemmaEntity> lemmaEntityList = actualizeLemmaEntityList(url);
        for (LemmaEntity lemmaEntity : lemmaEntityList){
            map.put(lemmaEntity, indexRepository.findPagesIdByLemmaIdInIsPageList(lemmaEntity.getId(), pageIdList));
        }
        return map;
    }

    private List<Integer> getPageIdListFromIndexRepo(){
        LemmaEntity lemmaEntity = getRelativeFrequency();
        return indexRepository.findPagesIdByLemmaIdIn(lemmaEntity.getLemma());
    }

    private LemmaEntity getRelativeFrequency(){
        log.info("Lemma: " + cachedLemmasToString);
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(cachedLemmasToString);
        return lemmaEntity;
    }

    private List<LemmaEntity> actualizeLemmaEntityList(String url){
        List<SiteEntity> siteEntityList = getSiteEntityList(url);
        List<Integer> siteIdList = siteEntityList.stream().map(SiteEntity::getId).collect(Collectors.toList());
        return lemmaRepository.findAllByLemmaNameAndSiteName(cachedLemmasToString, siteIdList);
    }

    private void getLemmasEntityToString(){
        cachedLemmasToString = cashedLemmasToLemmaEntityList.stream().map(LemmaEntity::getLemma).collect(Collectors.toList());
    }

    private void getLemmaEntityList(String query){
        log.info("line 189 query: {}", query);
        Set<String> lemmaList = LemmaExecute.getLemmaList(query);
        log.info("line 192 lemmaList: {}", lemmaList);
        List<LemmaEntity> lemmaEntityList = lemmaRepository.findAllByLemmaName(lemmaList);
        log.info("getLemmaEntityList by new array list " + lemmaEntityList.size() );
        cashedLemmasToLemmaEntityList.addAll(lemmaEntityList);
        log.info("getLemmaEntityList " + cashedLemmasToLemmaEntityList.size());
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

    private void buildStringListAndLemmaEntityList(String query){
        if(cachedLemmasToString != null && cashedLemmasToLemmaEntityList != null) {
            cashedLemmasToLemmaEntityList.clear();
            cachedLemmasToString.clear();
        }
        if(!checkQuery(query)) {
            getLemmaEntityList(query);
            getLemmasEntityToString();
        }
    }

    private boolean checkQuery(String query){
        return query == null || query.isEmpty();
    }
}