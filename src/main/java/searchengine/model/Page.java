package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "page")
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "site_id")
    private int siteId;

    @Column
    private String path;

    @Column
    private int code;

    @Column
    private String content;

    public Page() {
    }
}
