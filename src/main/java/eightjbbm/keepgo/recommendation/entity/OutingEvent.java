package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.util.File;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@NoArgsConstructor
public class OutingEvent {

    @Id @GeneratedValue
    private Long id;

    @OneToOne
    @JoinColumn(name = "image_id")
    private File attachedImage;

    //private EventCategory eventCategory;
    private String name;
    private String description;
    private Instant startAt;
    private Instant endAt;
}
