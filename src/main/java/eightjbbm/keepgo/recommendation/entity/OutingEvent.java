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
            String locationDetail,
            String address,
            Coordinate coordinate,
            @Nullable BusinessSchedule businessSchedule,
            @Nullable Instant startDatetime,
            @Nullable Instant endDatetime) {
        super(category, name, description, locationDetail, address, coordinate, businessSchedule);
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
    }

    @Override
    public boolean isAvailable(Instant instant) {
        if (this.getBusinessSchedule() == null || this.startDatetime == null || this.endDatetime == null) return false;
        return this.getBusinessSchedule().isAvailable(instant)
                && instant.isAfter(startDatetime)
                && instant.isBefore(endDatetime);
    }
}
