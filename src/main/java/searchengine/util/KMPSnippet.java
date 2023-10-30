package searchengine.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class KMPSnippet {

   public String cutSnippet(String text, String lemma) {
        List<Integer> result = KMPSearch(text, lemma);
        int startIndex = result.get(0) - 15;
        int finishIndex = result.get(0) + 15;
        log.info("startIndex: " + startIndex + " finishIndex: " + finishIndex + " result: " + result.get(0) + " " + result.get(1) + " text: " + text.length());
        return text.substring(startIndex, finishIndex);
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
