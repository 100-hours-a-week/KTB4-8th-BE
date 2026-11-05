package eightjbbm.keepgo.v4.extractor;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisRepository;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InterestExtractor {

    private final VideoAnalysisRepository videoAnalysisRepository;

    public List<OutingGuide> query(String videoId) {
        // 버전 체크
        String latestVersion;

        videoAnalysisRepository.findLatestByVideoId(videoId, latestVersion);

        // 최신 버전의 결과가 존재하고, 결과가 SUCCESS 혹은 NOT_FOUND이다: 그 결과를 사용하여 반환

        // 없거나, 있어도 FAILED이다: 작업 테이블에 제출 (비동기) -> 완료 후 반환

        return null;
    }
}
