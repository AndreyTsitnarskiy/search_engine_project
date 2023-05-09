package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j(topic = "worker")
@Service
@RequiredArgsConstructor
public class ParserSiteImpl implements ParserSite {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesList sitesList;
    private final ConnectionService connectionService;

    private final ForkJoinPool pool = new ForkJoinPool(8);

    @Override
    public void indexAllSites() throws IOException {
        saveSite(sitesList.getSites());
        List<SiteEntity> sites = siteRepository.findAll();
        for (SiteEntity site : sites) {
            log.info("поток: " + Thread.currentThread().getName() + " site " + site);
            Set<String> links = findAllLinks(site);
            log.info("-------------------------end site " + site);
            for (String link : links) {
                savePage(link, site);
            }
            log.info("-------------------------end add database " + site);
        }
    }

    private Set<String> findAllLinks(SiteEntity siteEntity) {
        Set<String> result = pool.invoke((new CrawlerTask(siteEntity.getUrl(), connectionService)));
        return result;
    }

    // Сохраняем страницу
    private void savePage(String url, SiteEntity siteEntity) throws IOException {
        PageEntity page = new PageEntity();
        page.setSiteId(siteEntity.getId());
        page.setCode(connectionService.httpResponseCode(url));
        page.setPath(url.replaceAll(siteEntity.getUrl(), ""));
        page.setContent(connectionService.content(url));
        pageRepository.save(page);
    }

    // Сохраняем сайт
    private void saveSite(List<Site> sites) {
        sites = sitesList.getSites();
        for (Site site : sites) {
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setLastError(null);
            siteEntity.setLocalDateTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
    }
}
