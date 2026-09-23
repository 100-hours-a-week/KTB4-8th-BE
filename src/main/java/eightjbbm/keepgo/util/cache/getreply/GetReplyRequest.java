package eightjbbm.keepgo.util.cache.getreply;

import java.time.LocalDate;
import java.util.List;

public record GetReplyRequest(
        Long memberId,
        String content,
        LocalDate createdDate,
        Float lat,
        Float lng,
        String region,
        LocalDate datetime,
        Integer availableTime,
        List<String> categories,
        String query
) {
    public static GetReplyRequest from(
            Long memberId,
            String content,
            LocalDate createdDate,
            Float lat,
            Float lng,
            String region,
            LocalDate datetime,
            Integer availableTime,
            List<String> category,
            String query
    ) {
        return new GetReplyRequest(
                memberId,
                content,
                createdDate,
                lat,
                lng,
                region,
                datetime,
                availableTime,
                category,
                query
        );
    }
}
