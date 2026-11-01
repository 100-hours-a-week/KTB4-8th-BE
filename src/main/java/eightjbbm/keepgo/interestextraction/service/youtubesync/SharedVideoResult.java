package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import java.util.List;

/// 다른 동기화가 끝낸 영상 분석 결과. 회원과 무관하게 같은 영상이면 공유하고, 가이드는 ID로만 가리킨다.
///
/// SUCCESS는 영상의 복수 가이드 전체, NOT_FOUND는 가이드 없음이다.
/// FAILED는 공유 분석 또는 실행권 판정 전 실패이며, 새 분석 이력 없이 실패 수에만 반영한다.
public record SharedVideoResult(String videoId, VideoAnalysisStatus status, List<Long> guideIds) {

    public static SharedVideoResult failed(String videoId) {
        return new SharedVideoResult(videoId, VideoAnalysisStatus.FAILED, List.of());
    }

    public boolean succeeded() {
        return status == VideoAnalysisStatus.SUCCESS;
    }
}
