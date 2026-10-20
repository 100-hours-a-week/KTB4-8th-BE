package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

public sealed interface GetReplyResult
        permits GetReplyResult.Completed, GetReplyResult.InProgress {

    record Completed(
            String status,
            Chat chat,
            Slot slot
    ) implements GetReplyResult {
        public static GetReplyResult from(String status, String content, OutingCriteria outingCriteria) {
            return new Completed(
                    status, new Chat(content, true),
                    new Slot(
                            outingCriteria.getUserCoordinate(),
                            outingCriteria.getRequestedLocationName(),
                            outingCriteria.getRequestedDatetime().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                            outingCriteria.getUserAvailableTimeMinutes(),
                            outingCriteria.getCategories()
                    )
            );
        }
    }

    public record Slot(
            Coordinate origin,
            String region,
            LocalDateTime datetime,
            Integer availableTime,
            Set<String> category
    ) {}

    record InProgress(
            String status,
            Integer pollAfter
    ) implements GetReplyResult {
        public static GetReplyResult from(String status, Integer pollAfter){
            return new InProgress(status, pollAfter);
        }
    }

    record Chat(
            String content,
            Boolean isByBot
    ) {}
}
