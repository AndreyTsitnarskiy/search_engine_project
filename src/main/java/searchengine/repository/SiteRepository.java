package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

    Optional<List<SiteEntity>> findByName(String name);

    @Transactional
    void deleteSiteEntityByUrl(String url);

    @Query(value = "SELECT * FROM site WHERE url = :url AND status = 'INDEXED'", nativeQuery = true)
    SiteEntity findSiteEntityByUrl(@Param("url") String url);

}
