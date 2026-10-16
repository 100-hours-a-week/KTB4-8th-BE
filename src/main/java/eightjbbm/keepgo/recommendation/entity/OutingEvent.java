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
@DiscriminatorValue("event")
@NoArgsConstructor
public class OutingEvent extends OutingGuide{

    @Nullable
    private Instant startDatetime;
    @Nullable
    private Instant endDatetime;

    public OutingEvent(
            String category,
            String name,
            String description,
            String address,
            Coordinate coordinate,
            @Nullable BusinessHours businessHours,
            @Nullable Instant startDatetime,
            @Nullable Instant endDatetime) {
        super(category, name, description, address, coordinate, businessHours);
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
    }

    @Override
    public boolean isAvailable(Instant instant) {
        if (this.getBusinessHours() == null || this.startDatetime == null || this.endDatetime == null) return false;
        return this.getBusinessHours().isAvailable(instant)
                && instant.isAfter(startDatetime)
                && instant.isBefore(endDatetime);
    }
}
