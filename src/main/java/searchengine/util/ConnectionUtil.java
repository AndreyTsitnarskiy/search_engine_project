package searchengine.util;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.exceptions.SiteExceptions;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Log4j2
@UtilityClass
public class ConnectionUtil {

    public static Connection getConnection(String pagePath, String userAgent, String referrer) {
        return Jsoup.connect(pagePath)
                .userAgent(userAgent)
                .referrer(referrer)
                .ignoreHttpErrors(true);
    }

    public Document parse(String html) {
        return Jsoup.parse(html);
    }

    public Connection.Response getResponse(Connection connection) {
        Connection.Response response;
        try {
            response = connection.execute();
        } catch (IOException e) {
            throw new SiteExceptions("Connection request failed while getting Response");
        }
        return response;
    }

    public Document getDocument(Connection connection) {
        Document document;
        try {
            document = connection.get();
        } catch (IOException e) {
            throw new SiteExceptions("Connection request failed while getting Document");
        }
        return document;
    }

    public int httpResponseCode(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        return connection.getResponseCode();
    }

    public String lastErrorResponse(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        return connection.getResponseMessage();
    }

    public String urlString(String url) {

        return url;
    }
}
