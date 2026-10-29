package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import eightjbbm.keepgo.common.domain.BusinessHoursMapper;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import java.time.Instant;
import java.time.ZoneId;

public class OutingGuideFactory {
    public static OutingGuide createOutingGuide(AnalyzeVideoResponseV2.Success.Result result) {
        String type = classifyType(result.category());
        Instant startDatetime = (result.eventStartDate() == null)
                ? null
                : result.eventStartDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endDatetime = (result.eventEndDate() == null)
                ? null
                : result.eventEndDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
        switch (type) {
            case "place" -> {
                return new OutingPlace(
                        result.category(),
                        result.placeName(),
                        result.summary(),
                        result.locationDetail(),
                        result.address(),
                        new Coordinate(result.lat(), result.lng()),
                        BusinessHoursMapper.toBusinessHours(result.businessHours()));
            }
            case "event" -> {
                return new OutingEvent(
                        result.category(),
                        result.placeName(),
                        result.summary(),
                        result.locationDetail(),
                        result.address(),
                        new Coordinate(result.lat(), result.lng()),
                        BusinessHoursMapper.toBusinessHours(result.businessHours()),
                        startDatetime,
                        endDatetime);
            }
            default -> throw new IllegalArgumentException();
        }
    }

    private static String classifyType(String type) {
        switch (type) {
            case "팝업", "전시" -> {
                return "event";
            }
            default -> {
                return "place";
            }
        }
    }
}
