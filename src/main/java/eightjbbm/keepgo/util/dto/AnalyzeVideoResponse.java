package eightjbbm.keepgo.util.dto;

import java.time.Instant;

public sealed interface AnalyzeVideoResponse {
    public record Success(
            String message,
            AnalyzedData data
    ) implements AnalyzeVideoResponse {
        public record AnalyzedData(
                String placeName,
                String region,
                String category,
                Instant eventStartDate,
                Instant eventEndDate,
                Float confidence,
                String summary
        ) {}

        public String getCategory() {
            return data().category();
        }

        public String getName() {
            return data().placeName();
        }

        public String getSummary() { return data().summary(); }
    }

    public record Error(
            String message,
            Object data
    ) implements AnalyzeVideoResponse {}
}