package searchengine.util;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.PropertySource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Log4j2
@UtilityClass
@PropertySource("classpath:application.yaml")
public class ConnectionUtil {

    public Document returnDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.142.86 Safari/537.36")
                .referrer("http://www.google.com")
                .timeout(2000)
                .maxBodySize(0)
                .get();
    }

    public int httpResponseCode(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        return connection.getResponseCode();
    }

    public String content(String urlString) throws IOException {
        if (httpResponseCode(urlString) == 200) {
            return returnDocument(urlString).html();
        }
        return "Error response code: " + httpResponseCode(urlString);
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
