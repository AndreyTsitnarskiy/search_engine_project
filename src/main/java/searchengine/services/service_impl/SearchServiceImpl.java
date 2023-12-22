package searchengine.services.service_impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.SearchService;
import searchengine.util.ConnectionUtil;
import searchengine.util.KMPSnippet;
import searchengine.util.LemmaExecute;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.currentTimeMillis;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final KMPSnippet kmpSnippet;
    private List<String> cachedLemmasToString;
    private List<LemmaEntity> cashedLemmasToLemmaEntityList = new ArrayList<>();
    private List<SiteEntity> cashedSiteEntityList = new ArrayList<>();

    @Override
    public ResponseEntity<ApiSearchResponse> search(String query, String url, int offset, int limit) {
        log.info("Received search request with query: {}, url: {}, offset: {}, limit: {}", query, url, offset, limit);
        getSiteEntityList(url);
        ApiSearchResponse apiSearchResponse = new ApiSearchResponse();
        apiSearchResponse.setResult(false);
        if(checkQuery(query) || checkLemmaInDatabase(query)){
            apiSearchResponse.setMessageError("Query is incorrect or empty");
        } else if (checkStatusSites(url)){
            apiSearchResponse.setMessageError("Page is located outside the sites specified in the configuration file");
        } else {
            buildStringListAndLemmaEntityList(query);
                apiSearchResponse = getApiSearchResponse();
        }
        return ResponseEntity.ok(apiSearchResponse);
    }

    private ApiSearchResponse getApiSearchResponse() {
        ApiSearchResponse apiSearchResponse = new ApiSearchResponse();
        apiSearchResponse.setResult(true);
        List<ApiSearchResult> apiSearchResultList = getApiSearchResult();
        apiSearchResponse.setCount(apiSearchResultList.size());
        apiSearchResponse.setData(apiSearchResultList);
        apiSearchResponse.setMessageError(null);
        return apiSearchResponse;
    }

    private List<ApiSearchResult> getApiSearchResult(){
        List<ApiSearchResult> apiSearchResultList = new ArrayList<>();
        Map<PageEntity, Float> result = sortPagesByRelationsValue();
        long start2 = currentTimeMillis();
        for (Map.Entry<PageEntity, Float> entry : result.entrySet()) {
            PageEntity pageEntity = entry.getKey();
            String snippet = execSnippet(pageEntity);
            if(snippet == null || snippet.isEmpty()){
                continue;
            }
            ApiSearchResult apiSearchResult = new ApiSearchResult();
            apiSearchResult.setSiteName(entry.getKey().getSite().getName());
            apiSearchResult.setSite(entry.getKey().getSite().getUrl());
            apiSearchResult.setUri(entry.getKey().getPath());
            apiSearchResult.setSnippet(snippet);
            apiSearchResult.setTitle(ConnectionUtil.getTextFromContentToSnippet(pageEntity.getContent()));
            apiSearchResult.setRelevance(entry.getValue());
            apiSearchResultList.add(apiSearchResult);
        }
        log.info("TIME getApiSearchResult: " + (currentTimeMillis() - start2));
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

    private Map<PageEntity, Float> sortPagesByRelationsValue(){
        Map<PageEntity, Float> map = getPagesByRelationsRanking();
        return map.entrySet().stream()
                .sorted(Map.Entry.<PageEntity, Float>comparingByValue().reversed()).limit(200)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
    }

    private Map<PageEntity, Float> getPagesByRelationsRanking(){
        Map<PageEntity, Float> map = new HashMap<>();
        Map<IndexEntity, Float> relativeResultRanking = getRelativeResultRanking();
        for (Map.Entry<IndexEntity, Float> entry : relativeResultRanking.entrySet()) {
            map.put(entry.getKey().getPage(), entry.getValue());
        }
        return map;
    }

    private Map<IndexEntity, Float> getRelativeResultRanking(){
        Map<IndexEntity, Float> map = getCalculateAbsoluteRanking();
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

    private Map<IndexEntity, Float> getCalculateAbsoluteRanking(){
        Map<IndexEntity, Float> map = new HashMap<>();
        Map<LemmaEntity, List<IndexEntity>> indexesForLemmaByPageId = getAllOfIndexesForLemmaByPageId();
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

    private Map<LemmaEntity, List<IndexEntity>> getAllOfIndexesForLemmaByPageId(){
        Map<LemmaEntity, List<IndexEntity>> map = new HashMap<>();
        long start = currentTimeMillis();
        List<Integer> pageIdList = getPageIdListFromIndexRepo();
        log.info("TIME getPageIdListFromIndexRepo: " + (currentTimeMillis() - start));
        List<LemmaEntity> lemmaEntityList = actualizeLemmaEntityList();
        long start2 = currentTimeMillis();
        for (LemmaEntity lemmaEntity : lemmaEntityList){
            map.put(lemmaEntity, indexRepository.findPagesIdByLemmaIdInIsPageList(lemmaEntity.getId(), pageIdList));
        }
        log.info("TIME getAllOfIndexesForLemmaByPageId: " + (currentTimeMillis() - start2));
        return map;
    }

    private List<Integer> getPageIdListFromIndexRepo(){
        LemmaEntity lemmaEntity = getRelativeFrequency();
        return indexRepository.findPagesIdByLemmaIdIn(lemmaEntity.getLemma());
    }

    private LemmaEntity getRelativeFrequency(){
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(cachedLemmasToString);
        return lemmaEntity;
    }

    private List<LemmaEntity> actualizeLemmaEntityList(){
        List<Integer> siteIdList = cashedSiteEntityList.stream().map(SiteEntity::getId).collect(Collectors.toList());
        return lemmaRepository.findAllByLemmaNameAndSiteName(cachedLemmasToString, siteIdList);
    }

    private void getLemmasEntityToString(){
        cachedLemmasToString = cashedLemmasToLemmaEntityList.stream().map(LemmaEntity::getLemma).collect(Collectors.toList());
    }

    private void getLemmaEntityList(String query){
        Set<String> lemmaList = LemmaExecute.getLemmaList(query);
        List<LemmaEntity> lemmaEntityList = lemmaRepository.findAllByLemmaName(lemmaList);
        cashedLemmasToLemmaEntityList.addAll(lemmaEntityList);
    }

    private void getSiteEntityList(String url){
        if (url == null || url.isEmpty()){
            cashedSiteEntityList = siteRepository.findAll();
        } else {
            SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
            if (siteEntity != null){
                cashedSiteEntityList.add(siteEntity);
            }
        }
    }

    private boolean checkLemmaInDatabase(String query){
        Set<String> lemmaList = LemmaExecute.getLemmaList(query);
        List<LemmaEntity> lemmaEntityList = lemmaRepository.findAllByLemmaName(lemmaList);
        return lemmaEntityList.isEmpty();
    }

    private boolean checkStatusSites(String url){
        List<SiteEntity> siteEntityList = new ArrayList<>();
        if (url == null) {
            siteEntityList = siteRepository.findAll();
        } else {
            SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
            siteEntityList.add(siteEntity);
        }
        log.info("check site by url: " + siteEntityList.size());
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