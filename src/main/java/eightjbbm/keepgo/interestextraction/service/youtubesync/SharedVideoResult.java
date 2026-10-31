package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import java.util.List;

/// 다른 동기화가 끝낸 영상 분석 결과. 회원과 무관하게 같은 영상이면 공유하고, 가이드는 ID로만 가리킨다.
///
/// SUCCESS는 영상의 복수 가이드 전체, NOT_FOUND는 가이드 없음, FAILED는 공유하던 분석이 실패했다는 뜻이다.
public record SharedVideoResult(String videoId, VideoAnalysisStatus status, List<Long> guideIds) {

    public static SharedVideoResult failed(String videoId) {
        return new SharedVideoResult(videoId, VideoAnalysisStatus.FAILED, List.of());
    }

    public boolean succeeded() {
        return status == VideoAnalysisStatus.SUCCESS;
    }
}
