package eightjbbm.keepgo.util.cache.slot;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotCacheService {
    private final CacheManager cacheManager;

    public SlotValue getSlot(Long memberId) {
        return null;
    }

    private Cache getSlotCache() {
        Cache cache = cacheManager.getCache("slot");

        if (cache == null) {
            throw new IllegalStateException("No Cache");
        }

        return cache;
    }
}
