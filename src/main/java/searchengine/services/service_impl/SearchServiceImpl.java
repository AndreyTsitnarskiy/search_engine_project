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
import searchengine.services.interfaces.LemmaService;
import searchengine.services.interfaces.SearchService;
import searchengine.util.PropertiesProject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PropertiesProject propertiesProject;
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
            log.info("Query: " + query + " URL: " + url);
            //apiSearchResponse = search(query, url);
            log.info("SITE ENTRY: " + getSiteEntityByUrl(url).getId() + " URL: " + getSiteEntityByUrl(url).getUrl());
            calculateAbsolutePages(query, getSiteEntityByUrl(url));
        }
        return ResponseEntity.ok(apiSearchResponse);
    }

    private boolean checkQuery(String query){
        return query == null && query.isEmpty() && query.matches("\\s+");
    }

    private boolean checkStatusSites(String url){
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
        log.info("SiteEntity: " + siteEntity.toString());
        if (siteEntity != null && siteEntity.getStatus().equals("INDEXED")){
            return true;
        }
        return false;
    }

    private SiteEntity getSiteEntityByUrl(String url){
        log.info("getSiteEntityByUrl " + url + " ");
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

    private Map<LemmaEntity, List<IndexEntity>> getAllIndexesForLemma(List<LemmaEntity> queryWords, SiteEntity siteEntity){
        log.info("getAllIndexesForLemma");
        Map<LemmaEntity, List<IndexEntity>> result = new HashMap<>();
        for (LemmaEntity lemmaEntity : queryWords) {
            log.info("lemmaEntity: " + lemmaEntity.getId());
            long lemmaId = lemmaEntity.getId();
            List<Integer> listPageId = indexRepository.findPagesIdByLemmaIdIn(lemmaId);
            List<IndexEntity> indexEntities = indexRepository.findPagesIdByLemmaIdInIsPageList(lemmaEntity.getId(),
                    listPageId);
            result.put(lemmaEntity, indexEntities);
        }
        return result;
    }

    //check point
    private Map<Integer, Float> calculateAbsolutePages(String query, SiteEntity siteEntity){
        log.info("calculateAbsolutePages");
        Map<Integer, Float> result = new HashMap<>();
        Map<LemmaEntity, List<IndexEntity>> allLemmasAndValuePages = getAllIndexesForLemma(getQueryWords(query, siteEntity), siteEntity);
        List<Integer> listNumberPages = getListPageId(query, siteEntity);
        for (int i = 0; i < listNumberPages.size(); i++) {
            float absolute = 0;
            for (Map.Entry<LemmaEntity, List<IndexEntity>> entry : allLemmasAndValuePages.entrySet()) {
                if (entry.getValue().equals(listNumberPages.get(i))) {
                    absolute += entry.getValue().get(listNumberPages.get(i)).getRank_lemmas();
                }
            }
            result.put(listNumberPages.get(i), absolute);
        }
        log.info("result: " + result.size() + " VALUES " + result.values());
        return result;
    }

    private Map<Integer, Float> calculateRelativePages(String query, SiteEntity siteEntity){
        Map<Integer, Float> result = new HashMap<>();
        Map<Integer, Float> absolutePages = calculateAbsolutePages(query, siteEntity);
        float maxAbsoluteValue = (float) absolutePages.values().stream()
                .mapToDouble(Float::doubleValue)
                .max()
                .orElse(0.0f);
        for (Map.Entry<Integer, Float> entry : absolutePages.entrySet()) {
            result.put(entry.getKey(), entry.getValue() / maxAbsoluteValue);
        }
        return result;
    }

    private List<Integer> getListPageId(String query, SiteEntity siteEntity){
        log.info("getListPageId " + query);
        Set<String> queryWords = lemmaService.getLemmaList(query);
        for (String word : queryWords) {
            log.info("queryWords cycle: " + word);
        }
        log.info("queryWords: " + queryWords.toString() + " " + queryWords.size() + " ");
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(siteEntity.getId(), queryWords);
        log.info("lemmaEntity: " + lemmaEntity.getLemma() + " " + lemmaEntity.getId());
        return indexRepository.findPagesIdByLemmaIdIn(lemmaEntity.getId());
    }
}
