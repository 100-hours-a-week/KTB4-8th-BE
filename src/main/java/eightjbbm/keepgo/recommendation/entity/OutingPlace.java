package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.util.File;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@DiscriminatorValue("PLACE")
@NoArgsConstructor
public class OutingPlace extends OutingGuide {
    private String googlePlaceId;

    public OutingPlace(String category, String name, String description) {
        super(category, name, description);
    }
}
