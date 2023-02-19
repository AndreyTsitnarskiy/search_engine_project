package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "lemma")
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "site_id")
    private int siteIdInLemma;

    @Column(name = "lemma")
    private String lemmaNormal;

    @Column
    private int frequency;

    public Lemma() {
    }
}
