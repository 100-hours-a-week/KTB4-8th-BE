package eightjbbm.keepgo.recommendation;

import java.time.LocalDate;
import java.util.List;

public record GetOngoingEventListResponse(List<Event> events) {
    public record Event(String name, String address, LocalDate startDate, LocalDate endDate) {}
}
