package eightjbbm.keepgo.util.cache.slot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SlotCacheService {
    private final SlotCacheRepository slotCacheRepository;

    public Optional<SlotValue> getSlot(Long memberId) {
        return slotCacheRepository.read(memberId);
    }

    public Optional<String> getQuery(Long memberId) { return slotCacheRepository.read(memberId).map(SlotValue::getQuery); }

    public void updateSlot(UpdateSlotCacheRequest request) {
        slotCacheRepository.update(
                request.memberId(),
                request
        );
    }
}
