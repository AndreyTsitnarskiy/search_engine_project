package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.model.Status;

import java.net.HttpURLConnection;
import java.net.URL;

import java.io.IOException;

@Service
public class ConnectionService {

    public Document returnDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .maxBodySize(0)
                .referrer("www.google.com")
                .timeout(10000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.207.132.170 Safari/537.36")
                .get();
    }

    public int httpResponseCode(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        return connection.getResponseCode();
    }

    public String content(String urlString) throws IOException {
        Document document = returnDocument(urlString);
        return document.select("html").text();
    }
}
