package searchengine.util;

import lombok.experimental.UtilityClass;
import org.jsoup.nodes.Element;
import searchengine.exceptions.SiteExceptions;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

@UtilityClass
public class ReworkString {
    private final String SLASH = "/";
    private final StringBuilder BUILDER = new StringBuilder();

    public String cutSlash(String siteNameWithSlash) {
        return siteNameWithSlash.substring(0, siteNameWithSlash.length() - 1);
    }

    public String getStartPage(String path) {
        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            throw new SiteExceptions("Site url is wrong");
        }
        String domain = url.getHost();
        domain = domain.startsWith("www.") ? domain.substring(4) : domain;
        return url.getProtocol() + "://" + domain + SLASH;
    }

    public String cutProtocolAndHost(String pagePath, String homePage) {
        String path = pagePath.substring(homePage.length());
        if (path.contains(".")) {
            path = path.substring(0, path.length() - 1);
        }
        path = path.startsWith(SLASH) ? path : SLASH + path;
        if (path.length() > 765) {
            throw new SiteExceptions("Path too long (length is " + path.length() + ")");
        }
        return path;
    }

    public boolean isStringExists(String s) {
        return !(s == null || s.matches("\\s+") || s.isEmpty());
    }

    public String getPathToSave(String pageUrl, String startPage) {
        int start = startPage.length();
        String pathToSave = pageUrl.replace("www.", "");
        pathToSave = SLASH + pathToSave.substring(start);
        pathToSave = pathToSave.endsWith(SLASH) || pathToSave.contains(".") ? pathToSave : pathToSave + SLASH;
        return pathToSave;
    }

    public boolean isHrefValid(String homePage, String href, String fileExtensions) {
        return href.startsWith(homePage)
                && isHrefToPage(href, fileExtensions)
                && !href.equals(homePage)
                && !href.equals(homePage + SLASH);
    }

    public boolean isPageAdded(Set<String> webpages, String href) {
        href += href.endsWith(SLASH) ? "" : SLASH;
        return webpages.contains(href);
    }

    public String getHrefFromAnchor(Element anchor) {
        String href = anchor.absUrl("href").trim().replace("\u00A0", "");
        href = href.endsWith(SLASH) ? href : href + SLASH;
        return href.replace("//www.", "//");
    }

    private boolean isHrefToPage(String href, String fileExtensions) {
        if (href.matches(".*([#?\"@\\\\]).*")) {
            return false;
        }
        return !href.matches(".*\\.(" + fileExtensions + ")/?");
    }
}
