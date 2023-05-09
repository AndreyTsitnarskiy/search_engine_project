package searchengine.model;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "Pages")
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @NonNull
    @Column(name = "site_id", nullable = false)
    private int siteId;

    @NonNull
    @Column(name = "path", nullable = false)
    private String path;

    @NonNull
    @Column(name = "code", nullable = false)
    private int code;

    @Column(columnDefinition = "LONGTEXT")
    private String content;


}
