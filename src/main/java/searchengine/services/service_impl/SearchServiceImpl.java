package searchengine.services.service_impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.api_search.ApiSearchResponse;
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
            //apiSearchResponse = search(query, url);
            findPagesByRelativePages(query, getSiteEntityByUrl(url));
        }
        return ResponseEntity.ok(apiSearchResponse);
    }

    //прверка строки запроса
    private boolean checkQuery(String query){
        return query == null && query.isEmpty() && query.matches("\\s+");
    }

    //проверка статуса сайта
    private boolean checkStatusSites(String url){
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
        if (siteEntity != null && siteEntity.getStatus().equals("INDEXED")){
            return true;
        }
        return false;
    }

    //вытащить сущность сайта из БД
    private SiteEntity getSiteEntityByUrl(String url){
        log.info("getSiteEntityByUrl " + url);
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url);
        if (siteEntity != null){
            return siteEntity;
        }
        return null;
    }

    //распарсить запрос на леммы
    private List<LemmaEntity> getQueryWords(String query, SiteEntity siteEntity){
        Set<String> queryWords = lemmaService.getLemmaList(query);
        return lemmaRepository.findByLemmaName(siteEntity.getId(), queryWords);
    }

    //вытащить страницы по леммам
    private List<Integer> getListPageId(String query, SiteEntity siteEntity){
        Set<String> queryWords = lemmaService.getLemmaList(query);
        LemmaEntity lemmaEntity = lemmaRepository.findByMinFrequency(siteEntity.getId(), queryWords);
        return indexRepository.findPagesIdByLemmaIdIn(lemmaEntity.getId());
    }

    //вытащить по всем лемам сущности индексов по страницам самой редко встречающейся леммы
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

    //рассчитать абсолют для все найденных страницах
    private Map<Integer, Float> calculateAbsolutePages(String query, SiteEntity siteEntity) {
        log.info("calculateAbsolutePages");
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
        log.info("result from calculateAbsolutePages: " + result.size() + " VALUES " + result.values());
        return result;
    }

    //поиск максимального абсольтного зачения
    private float findMaxValue(Map<Integer, Float> absolutePages){
        float maxAbsoluteValue = Float.MIN_VALUE;
        for (Map.Entry<Integer, Float> entry : absolutePages.entrySet()) {
            if(entry.getValue() > maxAbsoluteValue){
                maxAbsoluteValue = entry.getValue();
            }
        }
        return maxAbsoluteValue;
    }

    //расчитать относительные страницы по самому большому абсолютному значению
    private Map<Integer, Float> calculateRelativePages(String query, SiteEntity siteEntity){
        float maxAbsoluteValue = findMaxValue(calculateAbsolutePages(query, siteEntity));
        log.info("maxAbsoluteValue: " + maxAbsoluteValue);
        Map<Integer, Float> result = new HashMap<>();
        Map<Integer, Float> calcAbsolutePages = calculateAbsolutePages(query, siteEntity);
        for (Map.Entry<Integer, Float> entry : calcAbsolutePages.entrySet()) {
            //String formattedValue = String.format("%.2f", entry.getValue() / maxAbsoluteValue);
            result.put(entry.getKey(), entry.getValue() / maxAbsoluteValue);
        }
        log.info("result from calculateRelativePages: " + result.keySet() + " VALUES " + result.values());
        return result;
    }

    private Map<Float, PageEntity> findPagesByRelativePages(String query, SiteEntity siteEntity) {
        Map<Integer, Float> data = calculateRelativePages(query, siteEntity);
        Map<Float, PageEntity> result = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : data.entrySet()) {
            result.put(entry.getValue(), pageRepository.findById(entry.getKey()).get());
            log.info("result from findPagesByRelativePages: "
                    + siteEntity.getUrl().substring(0, siteEntity.getUrl().length() - 1)
                    + " " + pageRepository.findById(entry.getKey()).get().getPath());
        }
        return result;
    }
}
