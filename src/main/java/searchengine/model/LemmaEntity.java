package searchengine.model;

import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.List;

@Data
@RequiredArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lemmas")
public class LemmaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NonNull
    @Column(name = "lemma", nullable = false)
    private String lemma;

    @NonNull
    @ManyToOne
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteEntity site;

    @NonNull
    @Column(name = "frequency", nullable = false)
    private int frequency;

    @NonNull
    @OneToMany(mappedBy = "lemma", cascade = CascadeType.MERGE)
    private List<IndexEntity> indexEntityList;

}
