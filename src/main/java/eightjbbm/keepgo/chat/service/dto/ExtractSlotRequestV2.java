package eightjbbm.keepgo.chat.service.dto;

import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.util.Coordinate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

public record ExtractSlotRequestV2(String chat, LocalDate date, PrevSlot prevSlot, String prevQuery) {
    public record PrevSlot(
            Coordinate origin, String region, LocalDateTime datetime, String availableTime, Set<String> category) {}

    public static ExtractSlotRequestV2 from(String content, OutingCriteria criteria) {
        var zone = ZoneId.of("Asia/Seoul");
        var datetime = criteria.getRequestedDatetime();
        var minutes = criteria.getUserAvailableTimeMinutes();

        return new ExtractSlotRequestV2(
                content,
                LocalDate.now(zone),
                new PrevSlot(
                        criteria.getUserCoordinate(),
                        criteria.getRequestedLocationName(),
                        datetime == null ? null : datetime.atZone(zone).toLocalDateTime(),
                        minutes == null ? null : minutes.toString(),
                        criteria.getCategories()),
                criteria.getUserPreferenceSummary());
    }
}
