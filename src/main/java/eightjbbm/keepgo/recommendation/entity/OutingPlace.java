package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.sandbox.ds.BusinessHours;
import eightjbbm.keepgo.util.Coordinate;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@DiscriminatorValue("place")
@NoArgsConstructor
public class OutingPlace extends OutingGuide {

    public OutingPlace(
            String category,
            String name,
            String description,
            String address,
            Coordinate coordinate,
            @Nullable BusinessHours businessHours) {
        super(category, name, description, address, coordinate, businessHours);
    }

    @Override
    public boolean isAvailable(Instant instant) {
        return this.getBusinessHours() != null && this.getBusinessHours().isAvailable(instant);
    }
}
