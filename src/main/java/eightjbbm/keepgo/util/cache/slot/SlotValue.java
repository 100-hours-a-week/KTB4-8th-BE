package eightjbbm.keepgo.util.cache.slot;

import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class SlotValue {
    private String query;
    private Float lat;
    private Float lng;
    private String requestedLocationName;
    private LocalDate requestedDate;
    private Integer requestedTimeSlot;
    private Integer availableTime;
    private List<String> categories;
}
