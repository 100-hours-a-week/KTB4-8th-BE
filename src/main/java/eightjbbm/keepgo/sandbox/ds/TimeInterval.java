package eightjbbm.keepgo.sandbox.ds;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalTime;

@Embeddable
public record TimeInterval(
        @Column(name = "start_time") LocalTime startTime,
        @Column(name = "end_time") LocalTime endTime
) {
    public boolean crossesDateBoundary() {
        return startTime().isAfter(endTime);
    }

    public boolean isBetween(LocalTime time) {
        return startTime().isAfter(time) && endTime().isBefore(time);
    }
}
