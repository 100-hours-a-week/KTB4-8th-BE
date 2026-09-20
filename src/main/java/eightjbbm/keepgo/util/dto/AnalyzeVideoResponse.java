package eightjbbm.keepgo.util.dto;

import java.time.Instant;

public record AnalyzeVideoResponse(
        String message,
        AnalyzedData data
) {
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
