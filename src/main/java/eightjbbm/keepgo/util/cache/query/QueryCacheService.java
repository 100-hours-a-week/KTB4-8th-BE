package eightjbbm.keepgo.util.cache.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryCacheService {
    private final QueryCacheRepository queryCacheRepository;

    /// 저장된 정성 조건이 없으면 null
    public String getQuery(Long memberId) {
        return queryCacheRepository.read(memberId).orElse(null);
    }

    /// AI는 이전 query와 이번 발화를 합친 query를 돌려주므로 그대로 덮어쓴다. 비어 있으면 기존 값을 유지한다.
    public void updateQuery(Long memberId, String query) {
        if (query != null && !query.isBlank()) {
            queryCacheRepository.update(memberId, query);
        }
    }

    public void deleteQuery(Long memberId) {
        queryCacheRepository.delete(memberId);
    }
}
