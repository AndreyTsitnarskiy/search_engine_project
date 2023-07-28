package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.util.ConnectionUtil;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
@Service
@RequiredArgsConstructor
public class SiteParserImpl extends RecursiveTask<Set<String>> implements SiteParser {

    private String site;
    private static final Set<String> allSiteMap = Collections.synchronizedSet(new HashSet<>());
    private static final Lock lock = new ReentrantLock();
    private String regex = "([^\\s]+(\\.(?i)(jpg|png|gif|bmp|pdf))$)";

    public SiteParserImpl(String site) {
        this.site = site;
    }

    @Override
    protected Set<String> compute() {
        List<String> parsers = new ArrayList<>(parseSite(site));
        Set<SiteParserImpl> tasks = new HashSet<>();
        for (String child : parsers) {
            SiteParserImpl task = new SiteParserImpl(child);
            tasks.add(task);
            task.fork();
        }
        for (SiteParserImpl task : tasks) {
            allSiteMap.addAll(task.join());
        }
        return allSiteMap;
    }

    @Override
    public Set<String> parseSite(String site) {
        Set<String> result = new TreeSet<>();
        try {
            Document doc = ConnectionUtil.returnDocument(site);
            Elements elements = doc.select("a");
            for (Element element : elements) {
                String newUrl = element.attr("abs:href");
                if (newUrl.endsWith("/") && newUrl.startsWith(site) && !result.contains(newUrl)
                        && !allSiteMap.contains(newUrl) && !newUrl.matches(regex)) {
                    result.add(newUrl);
                }
            }
            for (String listElement : result) {
                String children = listElement;
                if (!allSiteMap.contains(children)) {
                //    lock.lock();
                //    try {
                        log.info("ADD PAGE: " + children);
                        allSiteMap.add(children);
                //    } finally {
                //        lock.unlock();
                //    }
                }
                Thread.sleep(250);
            }
        } catch (Exception ex) {
            log.error("ERROR PARSER SITE: " + ex.getMessage(), ex);
            ex.printStackTrace();
        }
        return result;
    }
}
