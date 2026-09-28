package eightjbbm.keepgo.util.cache.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryCacheService {
    private final QueryCacheRepository queryCacheRepository;

    public String getQuery(Long memberId) {
        return null;
    }
}
