package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    @Query(value = "SELECT page_id FROM indexes_table WHERE lemma_id IN " +
            "(SELECT id FROM lemmas WHERE lemma = :lemmaIds)", nativeQuery = true)
    List<Integer> findPagesIdByLemmaIdIn(@Param("lemmaIds") String lemmaIds);

    @Query(value = "SELECT * FROM indexes_table WHERE lemma_id = :lemmaIds" +
            " and page_id IN :pages", nativeQuery = true)
    List<IndexEntity> findPagesIdByLemmaIdInIsPageList(@Param("lemmaIds") long lemma,
                                         @Param("pages") List<Integer> pages);

}
