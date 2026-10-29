package eightjbbm.keepgo.sandbox.aiv2;

import eightjbbm.keepgo.sandbox.ds.BusinessScheduleDto;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;

public sealed interface AnalyzeVideoResponseV2 {
    record Success(String message, Data data) implements AnalyzeVideoResponseV2 {
        public record Data(List<Result> results) {}

        public record Result(
                String videoUrl,
                String status,
                String error,
                String placeName,
                String address,
                String locationDetail,
                String category,
                @Nullable BusinessScheduleDto businessHours,
                @Nullable LocalDate eventStartDate,
                @Nullable LocalDate eventEndDate,
                Object eventPeriodUnresolved,
                Float lat,
                Float lng,
                Float confidence,
                String summary) {}
    }

    record Failure(String message, Data data) implements AnalyzeVideoResponseV2 {
        public record Data() {}
    }
}
