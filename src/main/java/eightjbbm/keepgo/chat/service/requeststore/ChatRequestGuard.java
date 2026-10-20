package eightjbbm.keepgo.chat.service.requeststore;

import eightjbbm.keepgo.util.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatRequestGuard {

    private final ChatRequestStore store;

    public Lease acquire(Long memberId) {
        var lease = new Lease(store, memberId, UUID.randomUUID().toString());

        final boolean acquired;
        try {
            acquired = store.acquire(memberId, lease.requestId);
        } catch (RuntimeException error) {
            // Redis에 기록됐지만 응답만 유실되었을 가능성을 정리합니다.
            try {
                lease.close();
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }

        if (!acquired) {
            throw new ConflictException();
        }

        return lease;
    }

    @RequiredArgsConstructor
    public static final class Lease implements AutoCloseable {

        private final ChatRequestStore store;
        private final Long memberId;
        private final String requestId;

        private boolean released;

        @Override
        public synchronized void close() {
            if (released) {
                return;
            }

            RuntimeException failure = null;

            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    store.release(memberId, requestId);
                    released = true;
                    return;
                } catch (RuntimeException error) {
                    failure = error;
                }
            }

            throw failure;
        }
    }
}
