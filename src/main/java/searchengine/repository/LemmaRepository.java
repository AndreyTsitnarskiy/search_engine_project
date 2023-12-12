package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE lemmas SET frequency = frequency - 1 WHERE site_id = :siteId AND lemma IN :lemmas",
            nativeQuery = true)
    void reduceByOneLemmaFrequencies(@Param("siteId") int siteId, @Param("lemmas") Collection<String> lemmas);

    int countLemmasEntitiesBySite(SiteEntity siteEntity);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lemmas WHERE site_id = :siteId AND frequency < 1", nativeQuery = true)
    void deleteLemmasWithNoFrequencies(@Param("siteId") int siteId);

    @Query(value = "SELECT frequency FROM Lemmas WHERE site_id = :site_id AND lemma = :lemma", nativeQuery = true)
    int getFrequencyOccurrence(@Param("lemma") String lemma, @Param("site_id") int siteId);

    @Query(value = "SELECT * FROM Lemmas WHERE lemma IN :queryWords order by frequency asc", nativeQuery = true)
    List<LemmaEntity> findAllByLemmaName(@Param("queryWords") Set<String> queryWords);

    @Query(value = "SELECT * FROM Lemmas WHERE id IN :lemmaEntities and site_id IN :siteId", nativeQuery = true)
    List<LemmaEntity> findAllByLemmaNameAndSiteName(@Param("lemmaEntities") List<LemmaEntity> lemmaEntities,
                                                     @Param("siteId") List<SiteEntity> siteEntities);

    @Query(value = "SELECT * FROM Lemmas WHERE lemma IN :queryWords " +
            "AND frequency = (SELECT min(frequency) FROM Lemmas WHERE lemma IN :queryWords)", nativeQuery = true)
    LemmaEntity findByMinFrequency(@Param("queryWords") List<LemmaEntity> lemmaEntities);

    @Query(value = "SELECT * FROM Lemmas WHERE lemma = :word", nativeQuery = true)
    List<LemmaEntity> getLemmaAllSites(@Param("word") String word);
}
