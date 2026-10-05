package eightjbbm.keepgo.util.cache.slot;

import eightjbbm.keepgo.chat.dto.UpdateSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/// 회원의 의도 카드 정보 저장용 데이터
///
/// key: Long memberId
///
/// value: {@link SlotValue}
///
/// 생성: 의도 카드 업데이트 API
///
/// 조회:
///
/// 수정: 의도 카드 업데이트 API
///
/// 삭제: 여정 코스 추천 요청 API
@Repository
@RequiredArgsConstructor
public class SlotCacheRepository {
    public static final String cacheName = "slot";
    private final CacheManager cacheManager;

    public Optional<SlotValue> read(Long memberId) {
        return Optional.ofNullable(getSlotCache().get(memberId, SlotValue.class));
    }

    public void update(Long memberId, UpdateSlotCacheRequest request) {
        getSlotCache().putIfAbsent(memberId, SlotValue.createEmptySlotValue());
        var slotValue = getSlotCache().get(memberId, SlotValue.class);
        if (request.coordinate().lat() != null && request.coordinate().lng() != null) slotValue.setOrigin(request.coordinate());
        if (request.availableTime() != null) slotValue.setAvailableTime(request.availableTime());
        if (request.requestedLocationName() != null) slotValue.setRegion(request.requestedLocationName());
        if (request.requestedDateTime() != null) slotValue.setDatetime(request.requestedDateTime());
        if (request.categories() != null) slotValue.getCategory().addAll(request.categories());
    }

    public void update(Long memberId, ExtractSlotResponse.ExtractData result) {
        getSlotCache().putIfAbsent(memberId, SlotValue.createEmptySlotValue());
        var slotValue = getSlotCache().get(memberId, SlotValue.class);
        if (result.query() != null) slotValue.setQuery(result.query());
        if (result.slot().origin().lat() != null && result.slot().origin().lng() != null) slotValue.setOrigin(result.slot().origin());
        if (result.slot().availableTime() != null) slotValue.setAvailableTime(result.slot().availableTime());
        if (result.slot().region() != null) slotValue.setRegion(result.slot().region());
        if (result.slot().datetime() != null) slotValue.updateDatetime(result.slot().datetime());
        if (result.slot().category() != null) slotValue.addCategory(result.slot().category());
    }

    private Cache getSlotCache() {
        Cache cache = cacheManager.getCache(cacheName);

        if (cache == null) {
            throw new IllegalStateException("No Cache: " + cacheName);
        }

        return cache;
    }
}
