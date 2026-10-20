package eightjbbm.keepgo.chat.service.requeststore;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "keepgo.ephemeral.provider",
        havingValue = "caffeine",
        matchIfMissing = true
)
public class CaffeineChatRequestStore implements ChatRequestStore {

    private final Cache<Long, String> requests =
            Caffeine.newBuilder().build();

    @Override
    public boolean acquire(Long memberId, String requestId) {
        return requests.asMap().putIfAbsent(memberId, requestId) == null;
    }

    @Override
    public void release(Long memberId, String requestId) {
        requests.asMap().remove(memberId, requestId);
    }
}
