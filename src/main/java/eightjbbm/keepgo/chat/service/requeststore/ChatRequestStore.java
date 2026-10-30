package eightjbbm.keepgo.chat.service.requeststore;

import java.time.Duration;

public interface ChatRequestStore {
    Duration REQUEST_TTL = Duration.ofMinutes(10);

    boolean acquire(Long memberId, String requestId);

    void release(Long memberId, String requestId);
}
