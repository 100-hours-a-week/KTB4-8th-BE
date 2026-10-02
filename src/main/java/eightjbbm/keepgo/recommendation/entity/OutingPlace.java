package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.util.Coordinate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@DiscriminatorValue("PLACE")
@NoArgsConstructor
public class OutingPlace extends OutingGuide {
    //private Float lat;
    //private Float lng;

    public OutingPlace(/*Float lat, Float lng, */String category, String name, String description) {
        /*
        this.lat = lat;
        this.lng = lng;
         */
        super(category, name, description);
    }
}
