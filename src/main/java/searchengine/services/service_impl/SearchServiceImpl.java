package searchengine.services.service_impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.api_search.ApiSearchResponse;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.LemmaService;
import searchengine.services.interfaces.SearchService;
import searchengine.util.PropertiesProject;

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
    public ResponseEntity<ApiSearchResponse> search(String query, String url) {
        ApiSearchResponse apiSearchResponse = new ApiSearchResponse();
        apiSearchResponse.setResult(false);
        if(!checkQuery(query)){
            apiSearchResponse.setMessageError("Query is incorrect or empty");
        } else if (!checkStatusSites(url)){
            apiSearchResponse.setMessageError("Page is located outside the sites specified in the configuration file");
        } else {
            //apiSearchResponse = search(query, url);
        }
        return ResponseEntity.ok(apiSearchResponse);
    }

    private boolean checkQuery(String query){
        return query == null && query.isEmpty() && query.matches("\\s+");
    }

    private boolean checkStatusSites(String url){
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(url + "/");
        if (siteEntity != null && !siteEntity.getStatus().equals("INDEXED")){
            return false;
        }
        return true;
    }
}
