package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.util.File;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;


@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@NoArgsConstructor
@DiscriminatorColumn(name = "type")
public abstract class OutingGuide {
    @Id @GeneratedValue
    private Long id;

    @OneToOne
    @JoinColumn(name = "image_id")
    private File attachedImage;
    private String category;
    private String name;
    private String description;

    public OutingGuide(String category, String name, String description) {
        this.category = category;
        this.name = name;
        this.description = description;
    }
}
