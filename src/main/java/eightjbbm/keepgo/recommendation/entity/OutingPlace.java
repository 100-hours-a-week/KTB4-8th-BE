package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.common.domain.BusinessSchedule;
import eightjbbm.keepgo.util.Coordinate;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@DiscriminatorValue("place")
@NoArgsConstructor
public class OutingPlace extends OutingGuide {

    public OutingPlace(
            String category,
            String name,
            String description,
            String locationDetail,
            String address,
            Coordinate coordinate,
            @Nullable BusinessSchedule businessSchedule) {
        super(category, name, description, locationDetail, address, coordinate, businessSchedule);
    }

    @Override
    public boolean isAvailable(Instant instant) {
        return this.getBusinessSchedule() != null && this.getBusinessSchedule().isAvailable(instant);
    }

    @Override
    public GuideType guideType() {
        return GuideType.PLACE;
    }
}
