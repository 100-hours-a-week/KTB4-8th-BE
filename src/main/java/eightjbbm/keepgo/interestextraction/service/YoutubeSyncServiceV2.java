package eightjbbm.keepgo.interestextraction.service;

import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import eightjbbm.keepgo.interestextraction.repository.memberlike.MemberLikedVideoRepository;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisRepository;
import eightjbbm.keepgo.interestextraction.repository.youtubeanalyze.YoutubeApiServiceV2;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.sandbox.ds.VideoId;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YoutubeSyncServiceV2 {

    private final YoutubeApiServiceV2 youtubeApiService;
    private final MemberLikedVideoRepository memberLikedVideoRepository;
    private final VideoAnalysisRepository videoAnalysisRepository;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;

    // 좋아요 영상 조회 모듈
    public void getLikedVideos(Long memberId) {
        String pageToken = null;

        while (true) {
            VideoListResponse response;
            try {
                response = youtubeApiService.getVideoLists(memberId, pageToken);
            } catch (Exception e) {
                throw new ServiceUnavailableException();
            }

            // processVideos 요청
            // (논블로킹)
            processVideos(memberId, response.getItems());

            // 유튜브 API 응답 본문에 nextPageToken이 존재하지 않을 시(= null일 시): 다음 페이지가 존재하지 않으므로 조회를 중지한다
            pageToken = response.getNextPageToken();
            if (pageToken == null) {
                break;
            }
        }
    }

    // 최신화 필요 영상 처리 모듈
    public void processVideos(Long memberId, List<Video> videos) {
        // 3분 이하의 공개 상태인 업로드된 영상 중 MemberLikedVideo가 없는 영상
        List<String> unexistingVideoIds = memberLikedVideoRepository
                .findUnexistingVideoIds(
                        memberId,
                        videos.stream()
                                .filter(video ->
                                        Duration.parse(video.getContentDetails().getDuration()).compareTo(Duration.ofMinutes(3)) <= 0
                                        && video.getStatus().getPrivacyStatus().equals("public")
                                        && video.getStatus().getUploadStatus().equals("uploaded"))
                                .map(Video::getId)
                                .toList()
                );

    }

    // AI 분석 요청 / 결과 처리 모듈
    public void processAnalysisResult(Long memberId, List<String> videos) {

    }
}
