package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import eightjbbm.keepgo.common.domain.BusinessHoursMapper;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
import java.time.Instant;
import java.time.ZoneId;

public class OutingGuideFactory {
    /// 새 분석 계약의 후보를 저장 가능한 나들이 가이드로 만든다. 유형은 응답의 명시값을 쓴다.
    public static OutingGuide createOutingGuide(VideoAnalysisResponse.Guide guide) {
        Instant startDatetime = toInstant(guide.eventStartDate());
        Instant endDatetime = toInstant(guide.eventEndDate());
        Coordinate coordinate = new Coordinate(
                guide.lat() == null ? null : guide.lat().floatValue(),
                guide.lng() == null ? null : guide.lng().floatValue());

        return switch (guide.type()) {
            case PLACE ->
                new OutingPlace(
                        guide.category(),
                        guide.name(),
                        guide.description(),
                        guide.locationDetail(),
                        guide.address(),
                        coordinate,
                        BusinessHoursMapper.toBusinessHours(guide.businessHours()));
            case EVENT ->
                new OutingEvent(
                        guide.category(),
                        guide.name(),
                        guide.description(),
                        guide.locationDetail(),
                        guide.address(),
                        coordinate,
                        BusinessHoursMapper.toBusinessHours(guide.businessHours()),
                        startDatetime,
                        endDatetime);
            default -> throw new IllegalArgumentException("지원하지 않는 가이드 유형입니다: " + guide.type());
        };
    }

    private static Instant toInstant(java.time.LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
