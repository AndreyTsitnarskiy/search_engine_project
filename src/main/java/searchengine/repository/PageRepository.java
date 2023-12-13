package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    PageEntity findPageEntityByPathAndSite(String path, SiteEntity siteEntity);

    int countPageEntitiesBySite(SiteEntity siteEntity);

    @Query(value = "SELECT * FROM Pages WHERE id in :pageId", nativeQuery = true)
    PageEntity getPagesById(@Param("pageId") int pageId);
}
