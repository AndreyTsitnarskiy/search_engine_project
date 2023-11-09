package searchengine.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

@Slf4j
@UtilityClass
public class LemmaExecute {

    private LuceneMorphology morphology;
    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    static {
        try {
            morphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            log.error("Error initializing LuceneMorphology", e);
        }
    }

    public static HashMap<String, Integer> getLemmaMap(String html) {
        log.info("getLemmaMap method started");
        HashMap<String, Integer> lemmaMap = new HashMap<>();
        String[] words = arrayRussianWorlds(html);
        for (String word : words) {
            if(word.isBlank()){
                continue;
            }
            List<String> worldInfo = morphology.getMorphInfo(word);
            if(anyWordBaseBelongToParticle(worldInfo)) {
                continue;
            }

            List<String> normalizedWorldInfo = morphology.getNormalForms(word);
            if(normalizedWorldInfo.isEmpty()) {
                continue;
            }

            String normalizedWord = normalizedWorldInfo.get(0);

            if (lemmaMap.containsKey(normalizedWord)) {
                lemmaMap.put(normalizedWord, lemmaMap.get(normalizedWord) + 1);
            } else {
                lemmaMap.put(normalizedWord, 1);
            }
        }
        return lemmaMap;
    }

    public static Set<String> getLemmaList(String text) {
        String[] words = arrayRussianWorlds(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String word : words) {
            if(!word.isEmpty() && isRussianWord(word)) {
                List<String> worldInfo = morphology.getNormalForms(word);
                if(anyWordBaseBelongToParticle(worldInfo)) {
                    lemmaSet.add(word);
                    continue;
                }
                lemmaSet.addAll(worldInfo);
            }
        }
        return lemmaSet;
    }


    private String[] arrayRussianWorlds(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^а-я\\s]", "") .trim().split("\\s+");
    }

    private boolean isRussianWord(String word) {
        List<String> worldInfo = morphology.getMorphInfo(word);
        for (String world : worldInfo) {
            if (word.matches(WORD_TYPE_REGEX)) {
                return false;
            }
        }
        return true;
    }

    private boolean isParticle(String word) {
        for(String particle : particlesNames) {
            if(word.toUpperCase().contains(particle)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(LemmaExecute::isParticle);
    }
}

