package eightjbbm.keepgo.util.cache.slot;

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
    private final CacheManager cacheManager;

    public Optional<SlotValue> read(Long memberId) {
        return Optional.ofNullable(getSlotCache().get(memberId, SlotValue.class));
    }

    public void update(Long memberId, SlotValue slotValue) {
        getSlotCache().put(memberId, slotValue);
    }

    private Cache getSlotCache() {
        Cache cache = cacheManager.getCache("slot");

        if (cache == null) {
            throw new IllegalStateException("No Cache");
        }

        return cache;
    }
}
