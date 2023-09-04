package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import java.time.LocalDateTime;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

    @Transactional
    void deleteSiteEntityByUrl(String url);

    SiteEntity findSiteEntityByUrl(String url);

    boolean existsByStatus(Status status);
}
