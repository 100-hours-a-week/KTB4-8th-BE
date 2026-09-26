package eightjbbm.keepgo.util.cache.slot;

import eightjbbm.keepgo.util.Coordinate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotCacheService {
    private final SlotCacheRepository slotCacheRepository;

    public SlotValue read(Long memberId) {

        return null;
    }

    public Coordinate getCoordinate(Long memberId) {
        SlotValue slotValue = slotCacheRepository.read(memberId).orElseThrow();
        return slotValue.getCoordinate();
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
}
