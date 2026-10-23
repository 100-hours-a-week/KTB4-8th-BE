package eightjbbm.keepgo.util.cache.getreply;

import lombok.Getter;

@Getter
public class GetReplyValue{
    private GetReplyJobStatus status;
    private String reply;

    private GetReplyValue(GetReplyJobStatus status, String reply) {
        this.status = status;
        this.reply = reply;
    }

    public static GetReplyValue emptyValue() {
        return new GetReplyValue(GetReplyJobStatus.PENDING, null);
    }

    public void setStatusAsFail() {
        if (this.status == GetReplyJobStatus.PENDING) {
            this.status = GetReplyJobStatus.FAILED;
        }
    }
}
