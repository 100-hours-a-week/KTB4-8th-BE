package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.sandbox.ds.BusinessSchedule;
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
            @Nullable BusinessSchedule businessSchedule) {
        super(category, name, description, address, coordinate, businessSchedule);
    }

    @Override
    public boolean isAvailable(Instant instant) {
        return this.getBusinessSchedule() != null && this.getBusinessSchedule().isAvailable(instant);
    }
}
