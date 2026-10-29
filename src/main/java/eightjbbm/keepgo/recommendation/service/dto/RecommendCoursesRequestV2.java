package eightjbbm.keepgo.recommendation.service.dto;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.sandbox.ds.BusinessScheduleDto;
import eightjbbm.keepgo.util.Coordinate;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public record RecommendCoursesRequestV2(
        @Nullable String query,
        List<Candidate> candidates,
        String availableTime,
        Set<String> category,
        LocalDateTime datetime,
        Coordinate origin) {
    record Candidate(
            String placeId,
            String placeName,
            String summary,
            Float lat,
            Float lng,
            BusinessScheduleDto businessHours) {}

    public static RecommendCoursesRequestV2 from(OutingCriteria outingCriteria, List<OutingGuide> outingGuides) {
        return new RecommendCoursesRequestV2(
                outingCriteria.getUserPreferenceSummary(),
                outingGuides.stream()
                        .map(outingGuide -> new Candidate(
                                outingGuide.getId().toString(),
                                outingGuide.getName(),
                                outingGuide.getDescription(),
                                outingGuide.getCoordinate().lat(),
                                outingGuide.getCoordinate().lng(),
                                (outingGuide.getBusinessSchedule() == null)
                                        ? null
                                        : BusinessScheduleDto.from(outingGuide.getBusinessSchedule())))
                        .toList(),
                String.valueOf(outingCriteria.getUserAvailableTimeMinutes()),
                outingCriteria.getCategories(),
                outingCriteria
                        .getRequestedDatetime()
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toLocalDateTime(),
                outingCriteria.getUserCoordinate());
    }
}
