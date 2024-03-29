package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.exceptions.SiteExceptions;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.services.service_impl.IndexingServiceImpl;
import searchengine.util.ConnectionUtil;
import searchengine.util.ReworkString;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

@Log4j2
@RequiredArgsConstructor
public class SiteParser extends RecursiveAction {

    private final IndexingServiceImpl indexingService;
    private final SiteEntity siteEntity;
    private final String pagePath;

    @SneakyThrows
    @Override
    protected void compute() {
        try {
            Thread.sleep(500);
            handlePageData();
        } catch (UnsupportedMimeTypeException | ConnectException | SiteExceptions ignoredException) {
            log.warn("WARNING " + ignoredException + " IN CONNECTION WHILE HANDLING " + pagePath);
        } catch (Exception exception) {
            log.warn("WARNING " + exception + " IN CONNECTION WHILE HANDLING " + pagePath +
                    " INDEXING FOR SITE " + siteEntity.getUrl() + " COMPLETED TO FAIL");
            indexingService.getSiteStatusMap().put(siteEntity.getUrl(), Status.FAILED);
            throw exception;
        }
    }

    private void handlePageData() throws IOException {
        log.info("HANDING PAGE DATA: " + pagePath);
        List<SiteParser> pagesList = new ArrayList<>();
        String userAgent = indexingService.getPropertiesProject().getUserAgent();
        String referrer = indexingService.getPropertiesProject().getReferrer();
        Connection connection = ConnectionUtil.getConnection(pagePath, userAgent, referrer);
        int httpStatusCode = connection.execute().statusCode();
        if (httpStatusCode != 200) {
            connection = ConnectionUtil.getConnection(ReworkString.cutSlash(pagePath), userAgent, referrer);
            httpStatusCode = connection.execute().statusCode();
        }

        String pathToSave = ReworkString.cutProtocolAndHost(pagePath, siteEntity.getUrl());
        String html = "";
        PageEntity pageEntity = new PageEntity(siteEntity, pathToSave, httpStatusCode, html);
        if (httpStatusCode != 200) {
            indexingService.savePageAndSiteStatusTime(pageEntity, html, siteEntity);
        } else {
            Document document = connection.get();
            html = document.outerHtml();
            indexingService.savePageAndSiteStatusTime(pageEntity, html, siteEntity);
            log.info("Page indexed: " + pathToSave);
            indexingService.extractLemmas(html, pageEntity, siteEntity);
            Elements anchors = document.select("body").select("a");
            handleAnchors(anchors, pagesList);
        }
        for (SiteParser siteParser : pagesList) {
            siteParser.join();
        }
    }

    private void handleAnchors(Elements elements, List<SiteParser> parserList) {
        String fileExtensions = indexingService.getPropertiesProject().getFileExtensions();
        for (Element anchor : elements) {
            String href = ReworkString.getHrefFromAnchor(anchor);
            if (ReworkString.isHrefValid(siteEntity.getUrl(), href, fileExtensions)
                    && !ReworkString.isPageAdded(indexingService.getWebPages(), href)) {
                indexingService.getWebPages().add(href);
                if (!indexingService.getSiteStatusMap().get(siteEntity.getUrl()).equals(Status.INDEXING)) {
                    return;
                }
                SiteParser siteParser = new SiteParser(indexingService, siteEntity, href);
                parserList.add(siteParser);
                siteParser.fork();
            }
        }
    }
}
