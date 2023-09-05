package searchengine.model;

import lombok.*;

import javax.persistence.*;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "pages")
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @NonNull
    @ManyToOne(cascade = CascadeType.MERGE)
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false)
    private SiteEntity site;

    @NonNull
    @Column(name = "path", nullable = false, columnDefinition = "varchar(255)")
    private String path;

    @NonNull
    @Column(name = "code", nullable = false)
    private int code;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    public PageEntity(@NonNull SiteEntity siteEntity, @NonNull String path, @NonNull int code, String content) {
        this.site = siteEntity;
        this.path = path;
        this.code = code;
        this.content = content;
    }
}
