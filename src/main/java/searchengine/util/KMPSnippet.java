package searchengine.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class KMPSnippet {

    public String snippetFinishResult(String text, Set<String> lemmas) {
        StringBuilder sb = new StringBuilder();
        List<String> lemmasList = new ArrayList<>(lemmas);
        if(text == null) {
            return null;
        }
        for (int i = 0; i < lemmas.size(); i++) {
            List<Integer> result = KMPSearch(text.toLowerCase(), lemmasList.get(i));
            if(result.isEmpty() || result.get(0) == 0) {
                continue;
            }
            String boldWorld = boldWorld(text, result.get(0));
            sb.append(text.substring(1, result.get(0) - 1))
                    .append(" <b>").append(boldWorld).append("</b> ")
                    .append(text.substring(result.get(0) + boldWorld.length(), text.length()));
        }
        return sb.toString();
    }

    private String boldWorld(String text, int start) {
        StringBuilder sb = new StringBuilder();
        for(int i = start; i < text.length(); i++){
            if(text.charAt(i) == ' '){
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    public String idleSnippet(String text, String lemma) {
        String result = cutSnippet(text, lemma);
        if (result == null || result.length() == 0) {
            return null;
        }
        int[] snippetBounds = findSnippetBounds(result);
        return buildSnippet(result, snippetBounds);
    }

    public String cutSnippet(String text, String lemma) {
        Document document = Jsoup.parse(text);
        String body = document.body().text();
        List<Integer> result = KMPSearch(body, lemma);
        if (!result.isEmpty()) {
            int[] snippetBounds = calculateSnippetBounds(result, body.length(), lemma.length());
            return buildSnippet(body, snippetBounds);
        }
        return null;
    }

    private String buildSnippet(String text, int[] bounds) {
        StringBuilder sb = new StringBuilder();
        sb.append("... ").append(text.substring(bounds[0], bounds[1] + 1)).append(" ...");
        return sb.toString();
    }

    private int[] findSnippetBounds(String result) {
        char[] chars = result.toCharArray();
        int startIndex = 0;
        int endIndex = 0;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == ' ') {
                startIndex = i;
                break;
            }
        }
        for (int i = chars.length - 1; i >= 0; i--) {
            if (chars[i] == ' ') {
                endIndex = i;
                break;
            }
        }
        return new int[]{startIndex, endIndex};
    }

    private int[] calculateSnippetBounds(List<Integer> result, int bodyLength, int lemmaLength) {
        if (result.isEmpty()) {
            return new int[]{0, 0};
        }
        int startSubString = Math.max(0, result.get(0) - 100);
        int finishSubString = Math.min(result.get(0) + lemmaLength + 100, bodyLength);
        return new int[]{startSubString, finishSubString};
    }

    private int[] prefixFunction(String lemma) {
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
