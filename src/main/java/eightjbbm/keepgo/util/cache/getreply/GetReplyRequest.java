package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.chat.dto.SendChatCommand;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import java.time.LocalDate;

public record GetReplyRequest(Long memberId, String content, LocalDate createdDate, OutingCriteria outingCriteria) {

    public static GetReplyRequest from(SendChatCommand command, LocalDate date, OutingCriteria outingCriteria) {
        return new GetReplyRequest(command.memberId(), command.content(), date, outingCriteria);
    }
}
