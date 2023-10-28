package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    @Query(value = "SELECT page_id FROM indexes_tables WHERE lemma_id IN :lemmaIds", nativeQuery = true)
    List<Integer> findPagesIdByLemmaIdIn(@Param("lemmaIds") long lemmaIds);

    @Query(value = "SELECT * FROM indexes_tables WHERE lemma_id IN :lemmaIds and site_id = :siteId and page_id IN :pages", nativeQuery = true)
    List<IndexEntity> findPagesIdByLemmaIdIn(@Param("lemmaIds") long lemmaIds, @Param("siteId") int siteId,
                                         @Param("pages") List<Integer> pages);
}
