package eightjbbm.keepgo.sandbox;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.aiv2.AiServerService;
import eightjbbm.keepgo.sandbox.aiv2.AnalyzeVideoResponseV2;
import eightjbbm.keepgo.sandbox.ds.VideoIds;
import eightjbbm.keepgo.sandbox.memberlike.MemberLikedVideo;
import eightjbbm.keepgo.sandbox.memberlike.MemberLikedVideoRepository;
import eightjbbm.keepgo.sandbox.videoanalysis.*;
import eightjbbm.keepgo.sandbox.youtubeanalyze.YoutubeApiServiceV2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YoutubeAnalyzeService {

    private final MemberRepository memberRepository;
    private final YoutubeApiServiceV2 youtubeApiService;
    private final MemberLikedVideoRepository memberLikedVideoRepository; //좋아요 누른 동영상 목록 관리 테이블
    private final VideoAnalysisRepository videoAnalysisRepository; //분석 이력 관리 테이블
    private final VideoAnalysisResultRepository videoAnalysisResultRepository;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository; //여행 위시리스트 테이블
    private final OutingGuideRepository outingGuideRepository;
    private final AiServerService aiServerService;

    // 3분을 넘는 영상은 처리 대상이 아니다.
    private static final int MAX_VIDEO_LENGTH = 3;
    // AI 서버는 한 번에 최대 10개의 영상을 처리할 수 있다.
    private static final int CHUNK_SIZE = 10;

    public void analyzeVideo(Long memberId) throws Exception {
        String nextToken = null;
        int unprocessedCount = -1;
        while (true) {
            var response = youtubeApiService.getVideoLists(memberId, nextToken);

            if (nextToken == null) {
                int youtubeLikeCount = response.getPageInfo().getTotalResults();
                int keepgoLikeCount = memberLikedVideoRepository.countByMemberId(memberId).intValue();
                unprocessedCount = youtubeLikeCount - keepgoLikeCount;
            }

            var videos = response.getItems();
            int result = primaryProcess(memberId, videos);
            unprocessedCount -= result;

            if (unprocessedCount <= 0 || response.getNextPageToken() == null) { // unprocessedCount가 0보다 작아질 가능성은?
                break;
            } else {
                nextToken = response.getNextPageToken(); // 이 시점 이후로 nextToken은 Not Null임이 보장되어야 함
            }
        }
    }

    /// 시스템에 등록된 회원이 좋아요를 누른 동영상 목록과 API의 조회 결과 좋아요를 누른 동영상의 개수의 차이에 해당하는 영상이 존재하는 지를 빠르게 판정하여 추가적인 API 조회가 필요한지만 판단한다.
    /// @param memberId
    /// @param videos
    /// @return
    private int primaryProcess(Long memberId, List<Video> videos) {
        // 시스템에 동기화된 영상들
        var existingVideoIds = VideoIds.fromVideoIds(
                memberLikedVideoRepository.findExistingVideoIds(
                        memberId,
                        VideoIds.fromVideos(videos).getVideoIds())
        );
        // 회원이 좋아요를 누른 동영상이지만 시스템과 아직 동기화가 되지 않은 영상들
        List<Video> unprocessedVideos = videos.stream()
                .filter(video -> !existingVideoIds.contains(video.getId()))
                .toList();

        Map<Boolean, List<Video>> partitioned = unprocessedVideos.stream()
                .collect(Collectors.partitioningBy(this::checkVideoLength));

        List<String> processableVideoIds = partitioned.get(true).stream()
                .map(Video::getId)
                .toList();

        // 3분을 넘어가는 영상들은 너무 길다는 사유로 영상 분석 완료
        List<VideoAnalysis> results = partitioned.get(false).stream()
                .map(video -> new VideoAnalysis(video.getId(), VideoAnalysisStatus.TOO_LONG))
                .toList();
        videoAnalysisRepository.saveAll(results);

        secondaryProcess(memberId, processableVideoIds); // 논블로킹 처리 가능/필요

        return unprocessedVideos.size();
    }

    private boolean checkVideoLength(Video video) {
        return Duration.parse(video.getContentDetails().getDuration()).compareTo(Duration.ofMinutes(MAX_VIDEO_LENGTH)) <= 0;
    }

    private void secondaryProcess(Long memberId, List<String> videoIds) {
        List<String> unregisteredVideoIds = videoAnalysisRepository.findExistingVideoIds(memberId, videoIds);

        List<String> unanalyzedVideoIds = videoIds.stream()
                .filter(videoId -> !unregisteredVideoIds.contains(videoId))
                .toList();

        // AI 서버 대기열에 삽입 -> 성공한 요청들은 계속 작업
        for (int i = 0; i < unanalyzedVideoIds.size(); i += CHUNK_SIZE) {
            int to = Math.min(i + CHUNK_SIZE, videoIds.size());
            List<String> chunk = videoIds.subList(i, to);
            if (aiServerService.analyzeVideoV2(chunk) instanceof AnalyzeVideoResponseV2.Success response) {
                finalProcess(memberId, response);
            } else { // AI 서버 외부 API 예외 처리
                List<VideoAnalysis> failed = chunk.stream()
                        .map(videoId -> new VideoAnalysis(videoId, VideoAnalysisStatus.FAILED))
                        .toList();
                videoAnalysisRepository.saveAll(failed);
            }
        }

        // 분석 이력은 존재하는 영상들
        // 유튜브 영상 분석 이력 추출
        List<Long> videoAnalysisIds = videoAnalysisRepository.findIdsByVideoIds(memberId, unregisteredVideoIds);
        // 유튜브 영상 분석 이력에서 장소 및 이벤트 정보 추출
        List<OutingGuide> guides = videoAnalysisResultRepository.findOutingGuidesByVideoAnalysisIds(memberId, videoAnalysisIds);
        Member member = memberRepository.getReferenceById(memberId);
        List<OutingCollectionPrivate> entries = guides.stream()
                .map(outingGuide -> new OutingCollectionPrivate(member, outingGuide))
                .toList();
        // 위시리스트에 일괄 연결
        outingCollectionPrivateRepository.saveAll(entries);
    }

    public void finalProcess(Long memberId, AnalyzeVideoResponseV2.Success response) {
        List<VideoAnalysis> videoAnalyses = new ArrayList<>();
        List<VideoAnalysisResult> videoAnalysisResults = new ArrayList<>();
        List<MemberLikedVideo> memberLikedVideos = new ArrayList<>();
        List<OutingGuide> outingGuides = new ArrayList<>();
        List<OutingCollectionPrivate> outingCollectionPrivates = new ArrayList<>();

        Member member = memberRepository.getReferenceById(memberId);

        for (var result: response.data().results()) {
            String videoId = extractVideoIdFromUrl(result.videoUrl());
            memberLikedVideos.add(new MemberLikedVideo(videoId, member));

            var outingGuide = OutingGuideFactory.createOutingGuide(result);
            outingGuides.add(outingGuide);

            outingCollectionPrivates.add(new OutingCollectionPrivate(member, outingGuide));

            var videoAnalysis = new VideoAnalysis(videoId, result.status());
            videoAnalyses.add(videoAnalysis);
            if (result.status().equals("success")) {
                videoAnalysisResults.add(new VideoAnalysisResult(videoAnalysis, outingGuide));
            }
        }

        videoAnalysisRepository.saveAll(videoAnalyses);
        videoAnalysisResultRepository.saveAll(videoAnalysisResults);
        memberLikedVideoRepository.saveAll(memberLikedVideos);
        outingGuideRepository.saveAll(outingGuides);
        outingCollectionPrivateRepository.saveAll(outingCollectionPrivates);
    }

    private String extractVideoIdFromUrl(String url) {
        return url.split("/")[url.length() - 1];
    }
}
