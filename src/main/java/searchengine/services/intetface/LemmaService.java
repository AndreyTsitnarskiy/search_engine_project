package searchengine.services.intetface;

import searchengine.model.LemmaEntity;

import java.util.HashMap;
import java.util.Set;

public interface LemmaService {

    HashMap<String, LemmaEntity> getLemmaMap(String url);

    Set<String> getLemmaList(String text);
}
