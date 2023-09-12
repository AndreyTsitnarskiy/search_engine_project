package searchengine.config;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LemmaConfig {

    @Bean
    public LuceneMorphology getLuceneMorphology() throws Exception {
        return new RussianLuceneMorphology();
    }
}
