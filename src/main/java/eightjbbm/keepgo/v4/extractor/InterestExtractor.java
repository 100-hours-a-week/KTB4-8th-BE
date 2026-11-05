package eightjbbm.keepgo.v4.extractor;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterestExtractor {

    public List<OutingGuide> query(String videoId) {
        // 이력 기반 필터링

        // 없으면 -> 작업 테이블에 제출 (비동기)
        return null;
    }
}
