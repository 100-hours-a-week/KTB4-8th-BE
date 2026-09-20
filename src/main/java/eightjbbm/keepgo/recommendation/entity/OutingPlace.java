package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.util.File;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

@Entity
@DiscriminatorValue("PLACE")
@NoArgsConstructor
public class OutingPlace extends OutingGuide {
    private String googlePlaceId;

    public OutingPlace(String category, String name, String description) {
        super(category, name, description);
    }
}
