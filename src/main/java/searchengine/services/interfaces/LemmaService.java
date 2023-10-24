package searchengine.services.interfaces;

import java.util.HashMap;
import java.util.Set;

public interface LemmaService {

    HashMap<String, Integer> getLemmaMap(String url);

    Set<String> getLemmaList(String text);
}
