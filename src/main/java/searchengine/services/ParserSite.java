package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class ParserSite {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesList sitesList;
    private ForkJoinPool forkJoinPool;
    private final ConnectionService connectionService;

    private final String regex = "([^\\s]+(\\.(?i)(jpg|png|gif|bmp|pdf))$)";

    public void indexAllSites() {
        saveSite(sitesList.getSites());
        List<SiteEntity> sites = siteRepository.findAll();
        for (SiteEntity site : sites) {
            if (site.getStatus() == Status.INDEXING) {
                Set<String> visited = new HashSet<>();
                crawl(site.getUrl(), site, visited);
            }
        }
    }

    private Set<String> parser(String url) {
        Set<String> pages = new HashSet<>();
        try {
            Document document = connectionService.returnDocument(url);
            Elements elements = document.select("a[href]");
            for (Element element : elements) {
                String linkUrl = element.attr("abs:href");
                if (linkUrl.startsWith(url) && !linkUrl.matches(regex) &&
                        !linkUrl.contains("#") && !linkUrl.contains("?") && !linkUrl.contains("'")) {
                    pages.add(linkUrl);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return pages;
    }

    private void savePage(SiteEntity siteEntity, String url) throws IOException {
        PageEntity page = new PageEntity();
        page.setSiteId(siteEntity.getId());
        page.setCode(connectionService.httpResponseCode(url));
        page.setPath(url.replaceAll(siteEntity.getUrl(), ""));
        page.setContent(connectionService.content(url));
        pageRepository.save(page);
    }

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

    private void crawl(String url, SiteEntity siteEntity, Set<String> visited) {
        if (visited.contains(url)) {
            return;
        }
        visited.add(url);
        try {
            savePage(siteEntity, url);
            Set<String> links = parser(url);
            for (String link : links) {
                crawl(link, siteEntity, visited);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
