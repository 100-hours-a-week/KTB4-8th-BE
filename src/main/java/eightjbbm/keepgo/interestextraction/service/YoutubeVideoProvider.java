package eightjbbm.keepgo.interestextraction.service;

import com.google.api.services.youtube.model.VideoListResponse;

/// YouTube에서 좋아요한 영상을 조회하는 어댑터.
///
/// 동기화 통합 테스트는 이 경계를 스텁으로 바꿔 페이지·토큰 만료·조회 오류를 재현한다.
public interface YoutubeVideoProvider {

    VideoListResponse getVideoLists(Long memberId, String pageToken);
}
