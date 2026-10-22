package eightjbbm.keepgo.chat.service.requeststore;

public interface ChatRequestStore {
    boolean acquire(Long memberId, String requestId);

    void release(Long memberId, String requestId);
}
