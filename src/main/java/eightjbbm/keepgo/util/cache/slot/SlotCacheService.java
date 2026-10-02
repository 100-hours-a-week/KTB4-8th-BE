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

    public void updateSlot(UpdateSlotCacheRequest request) {
        slotCacheRepository.update(
                request.memberId(),
                SlotValue.from(
                        request.coordinate(),
                        request.requestedLocationName(),
                        request.requestedDateTime(),
                        request.availableTime(),
                        request.categories()
                )
        );
    }

    public void deleteSlot(Long memberId) {
        slotCacheRepository.delete(memberId);
    }
}
