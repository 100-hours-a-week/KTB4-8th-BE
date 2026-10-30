package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.OutingGuideFactory;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// AI 응답 하나(배치 하나)를 영상별 최종 상태와 가이드 목록으로 정리한 결과.
///
/// 응답을 받지 못한 영상, 알 수 없는 상태, 가이드 생성에 실패한 영상은 모두 FAILED로 남긴다.
public record BatchAnalysis(List<AnalyzedVideo> videos) {

    private static final String SHORTS_PREFIX = "https://youtube.com/shorts/";

    public record AnalyzedVideo(String videoId, VideoAnalysisStatus status, List<OutingGuide> guides) {

        public boolean succeeded() {
            return status == VideoAnalysisStatus.SUCCESS;
        }
    }

    /// AI 호출 자체가 실패한 배치. 모든 영상을 FAILED로 기록한다.
    public static BatchAnalysis failed(List<String> videoIds) {
        return new BatchAnalysis(distinct(videoIds).stream()
                .map(videoId -> new AnalyzedVideo(videoId, VideoAnalysisStatus.FAILED, List.of()))
                .toList());
    }

    public static BatchAnalysis from(List<String> videoIds, AnalyzeVideoResponseV2 response) {
        List<String> ids = distinct(videoIds);
        if (!(response instanceof AnalyzeVideoResponseV2.Success success)
                || success.data() == null
                || success.data().results() == null) {
            return failed(ids);
        }

        Map<String, VideoAnalysisStatus> statuses = new LinkedHashMap<>();
        Map<String, List<OutingGuide>> guidesByVideo = new HashMap<>();
        ids.forEach(videoId -> statuses.put(videoId, VideoAnalysisStatus.FAILED));

        Set<String> processed = new HashSet<>();
        for (var result : success.data().results()) {
            String videoId = videoIdOf(result.videoUrl());
            if (videoId == null || !statuses.containsKey(videoId) || !processed.add(videoId)) {
                continue;
            }

            switch (result.status() == null ? "" : result.status()) {
                case "success" -> {
                    OutingGuide guide = createGuide(result);
                    if (guide == null) {
                        statuses.put(videoId, VideoAnalysisStatus.FAILED);
                    } else {
                        guidesByVideo.put(videoId, List.of(guide));
                        statuses.put(videoId, VideoAnalysisStatus.SUCCESS);
                    }
                }
                case "not_found" -> statuses.put(videoId, VideoAnalysisStatus.NOT_FOUND);
                default -> statuses.put(videoId, VideoAnalysisStatus.FAILED);
            }
        }

        return new BatchAnalysis(ids.stream()
                .map(videoId -> new AnalyzedVideo(
                        videoId, statuses.get(videoId), guidesByVideo.getOrDefault(videoId, List.of())))
                .toList());
    }

    /// 처리된 영상 수. 실패한 영상도 포함한다.
    public int processedCount() {
        return videos.size();
    }

    /// 실패한 영상 수. NOT_FOUND와 FAILED를 모두 포함한다.
    public int failedCount() {
        return (int) videos.stream().filter(video -> !video.succeeded()).count();
    }

    public List<String> videoIds() {
        return videos.stream().map(AnalyzedVideo::videoId).toList();
    }

    /// 이 배치의 모든 가이드. 같은 가이드가 여러 영상에 나올 수 있으므로 중복을 허용한다.
    public List<OutingGuide> guides() {
        List<OutingGuide> guides = new ArrayList<>();
        videos.forEach(video -> guides.addAll(video.guides()));
        return List.copyOf(guides);
    }

    private static OutingGuide createGuide(AnalyzeVideoResponseV2.Success.Result result) {
        try {
            return OutingGuideFactory.createOutingGuide(result);
        } catch (RuntimeException error) {
            // 후보 하나가 잘못돼도 같은 배치의 다른 영상 결과는 유지한다.
            return null;
        }
    }

    private static String videoIdOf(String videoUrl) {
        if (videoUrl == null || !videoUrl.startsWith(SHORTS_PREFIX)) {
            return null;
        }
        String videoId = videoUrl.substring(SHORTS_PREFIX.length());
        return videoId.isBlank() ? null : videoId;
    }

    private static List<String> distinct(List<String> videoIds) {
        return videoIds.stream().distinct().toList();
    }
}
