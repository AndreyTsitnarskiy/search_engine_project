package searchengine.services.service_impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.api_search.ApiSearchResponse;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.SearchService;
import searchengine.util.KMPSnippet;
import searchengine.util.LemmaExecute;

import java.util.*;

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
            //apiSearchResponse = getApiSearchResponse(query, url);
        }
        return ResponseEntity.ok(apiSearchResponse);
    }

    private Map<LemmaEntity, List<IndexEntity>> getAllOfIndexesForLemmaByPageId(String query, String url){
        Map<LemmaEntity, List<IndexEntity>> map = new HashMap<>();
        List<Integer> pageIdList = getPageIdListFromIndexRepo(query, url);
        List<LemmaEntity> lemmaEntityList = atualizeLemmaEntityList(query, url);
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
        List<LemmaEntity> lemmaEntityList = atualizeLemmaEntityList(query, url);
        lemmaEntity = lemmaRepository.findByMinFrequency(lemmaEntityList);
        return lemmaEntity;
    }

    private List<LemmaEntity> atualizeLemmaEntityList(String query, String url){
        List<LemmaEntity> lemmaEntityList = getLemmaEntityList(query);
        List<SiteEntity> siteEntityList = getSiteEntityList(url);
        return lemmaRepository.findAllByLemmaNameAndSiteName(lemmaEntityList, siteEntityList);
    }

    private List<LemmaEntity> getLemmaEntityList(String query){
        log.info("query: {}", query);
        List<LemmaEntity> lemmaEntityList;
        Set<String> lemmaList = LemmaExecute.getLemmaList(query);
        log.info("lemmaList: {}", lemmaList);
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
