package searchengine.services;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrawlerTask extends RecursiveTask<Set<String>> {

    private final String url;
    private final ConnectionService connectionService;
    private static Set<String> allLinks = ConcurrentHashMap.newKeySet();
    private final String regex = "([^\\s]+(\\.(?i)(jpg|png|gif|bmp|pdf))$)";
    private final Logger LOGGER = LogManager.getLogger("worker");

    public CrawlerTask(String url, ConnectionService connectionService) {
        this.url = url;
        this.connectionService = connectionService;
    }

    @Override
    protected Set<String> compute() {
        allLinks.add(url);
        Set<String> childrenLinks = this.crawl(url);
        Set<CrawlerTask> taskList = new HashSet<>();
        for (String child : childrenLinks) {
            taskList.add((CrawlerTask) new CrawlerTask(child, connectionService).fork());
            LOGGER.info("поток: " + Thread.currentThread().getName() + " child " + child);
        }
        for (CrawlerTask task : taskList) {
            allLinks.addAll(task.join());
        }
        return allLinks;
    }

    private Set<String> crawl(String url) {
        Set<String> pages = new HashSet<>();
        try {
            Document document = connectionService.returnDocument(url);
            Elements elements = document.select("a[href]");
            for (Element element : elements) {
                String linkUrl = element.attr("abs:href");
                if (linkUrl.startsWith(url) && !linkUrl.matches(regex) &&
                        !linkUrl.contains("#") && !linkUrl.contains("?") && !linkUrl.contains("'") && !allLinks.contains(linkUrl)) {
                    pages.add(linkUrl);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("ОШИБКА В ПОТОКЕ " + Thread.currentThread().getName() + "\nСООБЩЕНИЕ" + e.getMessage());
        }
        return pages;
    }
}
