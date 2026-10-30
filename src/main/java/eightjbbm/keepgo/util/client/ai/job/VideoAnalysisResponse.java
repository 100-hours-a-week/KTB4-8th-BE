package eightjbbm.keepgo.util.client.ai.job;

import eightjbbm.keepgo.common.domain.BusinessScheduleDto;
import java.time.LocalDate;
import java.util.List;

public record VideoAnalysisResponse(List<Result> results) {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        FAILED,
        /// 알 수 없는 상태. 해당 영상만 실패로 처리할 수 있도록 배치 전체를 깨뜨리지 않는다.
        UNKNOWN
    }

    public enum GuideType {
        PLACE,
        EVENT,
        /// 알 수 없는 유형. 해당 영상만 실패로 처리할 수 있도록 배치 전체를 깨뜨리지 않는다.
        UNKNOWN
    }

    public record Failure(String code, String message) {}

    public record Result(String videoId, Status status, Failure error, List<Guide> guides) {}

    public record Guide(
            GuideType type,
            String name,
            String address,
            Double lat,
            Double lng,
            String description,
            String locationDetail,
            String category,
            BusinessScheduleDto businessHours,
            LocalDate eventStartDate,
            LocalDate eventEndDate,
            Boolean eventPeriodUnresolved,
            Double confidence) {}
}
