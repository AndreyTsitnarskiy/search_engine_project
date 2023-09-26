package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;

import java.util.Collection;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE lemmas SET frequency = frequency - 1 WHERE site_id = :siteId AND lemma IN :lemmas", nativeQuery = true)
    void overwriteLemmaFrequencies(@Param("siteId")  int siteId, @Param("lemmas") Collection<String> lemmas);

    @Query(value = "UPDATE lemmas SET frequency = frequency + 1 WHERE lemmas :lemma", nativeQuery = true)
    int updateFrequencyLemmasEntity(@Param("lemma") String lemma);
}
