package searchengine.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class KMPSnippet {

    public String idleSnippet(String text, String lemma) {
        StringBuilder sb = new StringBuilder();
        String result = cutSnippet(text, lemma);
        if(result == null || result.length() == 0) {
            return null;
        }
        char[] chars = result.toCharArray();
        int startIndex = 0;
        int endIndex = 0;
        for (int i = 0; i < chars.length; i++) {
            if(chars[i] == ' ') {
                startIndex = i;
                break;
            }
        }
        for (int i = chars.length - 1; i >= 0; i--) {
            if(chars[i] == ' ') {
                endIndex = i;
                break;
            }
        }

        sb.append("... ").append(result.substring(startIndex, endIndex + 1)).append(" ...");
        return sb.toString();
    }

    public String cutSnippet(String text, String lemma) {
        StringBuilder sb = new StringBuilder();
        Document document = Jsoup.parse(text);
        String body = document.body().text();
        List<Integer> result = KMPSearch(body, lemma);
        if (result.size() != 0) {
            int startSubString = result.get(0);
            if (result.get(0) > 40) {
                startSubString = startSubString - 40;
            }
            int finishSubString = result.get(0);
            int startSubString2 = result.get(0) + lemma.length();
            int finishSubString2 = result.get(0) + lemma.length();
            if (result.get(0) + lemma.length() < body.length()) {
                finishSubString2 = finishSubString2 + 40;
            }

            sb.append(body.substring(startSubString, finishSubString))
                    .append("<b>")
                    .append(lemma)
                    .append("</b>")
                    .append(body.substring(startSubString2, finishSubString2));
            return sb.toString();
        }
        return null;
    }

    private int[] prefixFunction(String lemma) {
        log.info("prefix function: " + lemma);
        int[] prefix = new int[lemma.length()];
        for (int i = 1; i < lemma.length(); i++) {
            int j = 0;
            while (i + j < lemma.length() && lemma.charAt(j) == lemma.charAt(i + j)) {
                prefix[i + j] = Math.max(prefix[i + j], j + 1);
                j++;
            }
        }
        return prefix;
    }

    private List<Integer> KMPSearch(String text, String lemma) {
        int[] prefix = prefixFunction(lemma);
        List<Integer> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < text.length()) {
            if (text.charAt(i) == lemma.charAt(j)) {
                i++;
                j++;
                if (j == lemma.length()) {
                    result.add(i - j);
                    j = prefix[j - 1];
                }
            } else if (i < text.length() - 1 && text.charAt(i + 1) == lemma.charAt(j)) {
                i += 2;
                j++;
            } else {
                if (j != 0) {
                    j = prefix[j - 1];
                } else {
                    i++;
                }
            }
        }
        return result;
    }
}
