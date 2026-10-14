package eightjbbm.keepgo.sandbox.ds;

import java.time.LocalTime;

public record TimeInterval(
        LocalTime startTime,
        LocalTime endTime
) {
    public boolean crossesDateBoundary() {
        return startTime().isAfter(endTime);
    }

    public boolean isBetween(LocalTime time) {
        return startTime().isAfter(time) && endTime().isBefore(time);
    }
}
