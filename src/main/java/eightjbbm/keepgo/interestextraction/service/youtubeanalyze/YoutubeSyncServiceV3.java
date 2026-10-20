package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import eightjbbm.keepgo.interestextraction.repository.youtubeanalyze.YoutubeApiServiceV2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class YoutubeSyncServiceV3 {

    private final YoutubeApiServiceV2 youtubeApiService;
    private final UpdateMemberLikedVideoService updateMemberLikedVideoService;

    /// 유튜브 API에서 회원과 연결된 유튜브 계정의 좋아요 표시한 동영상 목록을 불러온다.
    ///
    /// 응답으로 1개의 페이지를(최대 50개의 결과) 반환하므로 페이지 순회 필요
    public void initial(Long memberId) {
        String pageToken = null;
        do {
            // pageToken = null인 경우 pageToken을 요청에 포함하지 않는다 => 첫 페이지 조회
            var response = youtubeApiService.getVideoLists(memberId, pageToken);
            // 비동기 논블로킹 필요
            updateMemberLikedVideoService.updateMemberLikedVideos(
                    memberId,
                    response.getItems());

            pageToken = response.getNextPageToken();
        } while (pageToken != null);
    }
}
