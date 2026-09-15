package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.file.entity.File;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
public class OutingPlace {

    @Id @GeneratedValue
    private Long id;

    @OneToOne
    @JoinColumn(name = "image_id")
    private File attachedImage;

    //private PlaceCategory category;
    private String name;
    private String description;
    private String googlePlaceId;
}
