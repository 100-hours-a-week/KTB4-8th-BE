package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.ExcludedCandidate;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// AI 응답 하나(배치 하나)를 영상별 최종 상태와 가이드 목록으로 정리한 결과.
///
/// 응답을 받지 못한 영상, 알 수 없는 상태·유형, 후보가 모두 제외된 영상은 FAILED로 남긴다.
/// 후보 일부만 제외된 영상은 남은 후보로 SUCCESS가 된다.
public record BatchAnalysis(List<AnalyzedVideo> videos) {

    public static final String INVALID_AI_RESPONSE = "INVALID_AI_RESPONSE";
    public static final String INVALID_GUIDE_DATA = "INVALID_GUIDE_DATA";

    public record AnalyzedVideo(
            String videoId,
            VideoAnalysisStatus status,
            String failureCode,
            List<OutingGuide> guides,
            List<ExcludedCandidate> excludedCandidates) {

        public boolean succeeded() {
            return status == VideoAnalysisStatus.SUCCESS;
        }
    }

    /// AI 호출 자체가 실패한 배치. 모든 영상을 FAILED로 기록한다.
    public static BatchAnalysis failed(List<String> videoIds) {
        return failed(videoIds, INVALID_AI_RESPONSE);
    }

    public static BatchAnalysis failed(List<String> videoIds, String failureCode) {
        return new BatchAnalysis(distinct(videoIds).stream()
                .map(videoId -> failedVideo(videoId, failureCode))
                .toList());
    }

    public static BatchAnalysis from(List<String> videoIds, VideoAnalysisResponse response) {
        List<String> ids = distinct(videoIds);
        Map<String, VideoAnalysisResponse.Result> byVideoId = new LinkedHashMap<>();
        if (response != null && response.results() != null) {
            response.results().forEach(result -> {
                if (result != null && result.videoId() != null) {
                    byVideoId.putIfAbsent(result.videoId(), result);
                }
            });
        }

        return new BatchAnalysis(ids.stream()
                .map(videoId -> analyze(videoId, byVideoId.get(videoId)))
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

    private static AnalyzedVideo analyze(String videoId, VideoAnalysisResponse.Result result) {
        if (result == null) {
            return failedVideo(videoId, INVALID_AI_RESPONSE);
        }
        return switch (result.status()) {
            case SUCCESS -> analyzeGuides(videoId, result.guides());
            case NOT_FOUND -> new AnalyzedVideo(videoId, VideoAnalysisStatus.NOT_FOUND, null, List.of(), List.of());
            case FAILED ->
                new AnalyzedVideo(
                        videoId,
                        VideoAnalysisStatus.FAILED,
                        result.error() == null ? null : result.error().code(),
                        List.of(),
                        List.of());
            case UNKNOWN -> failedVideo(videoId, INVALID_AI_RESPONSE);
        };
    }

    private static AnalyzedVideo analyzeGuides(String videoId, List<VideoAnalysisResponse.Guide> guides) {
        if (guides == null || guides.isEmpty()) {
            return failedVideo(videoId, INVALID_AI_RESPONSE);
        }
        if (guides.stream()
                .anyMatch(guide -> guide == null
                        || guide.type() == null
                        || guide.type() == VideoAnalysisResponse.GuideType.UNKNOWN)) {
            return failedVideo(videoId, INVALID_AI_RESPONSE);
        }

        List<OutingGuide> validGuides = new ArrayList<>();
        List<ExcludedCandidate> excluded = new ArrayList<>();
        for (int index = 0; index < guides.size(); index++) {
            var validation = OutingGuideCandidateValidator.validate(index, guides.get(index));
            if (validation.isValid()) {
                validGuides.add(validation.guide());
            } else {
                excluded.add(validation.excluded());
            }
        }

        if (validGuides.isEmpty()) {
            return new AnalyzedVideo(
                    videoId, VideoAnalysisStatus.FAILED, INVALID_GUIDE_DATA, List.of(), List.copyOf(excluded));
        }
        return new AnalyzedVideo(
                videoId, VideoAnalysisStatus.SUCCESS, null, List.copyOf(validGuides), List.copyOf(excluded));
    }

    private static AnalyzedVideo failedVideo(String videoId, String failureCode) {
        return new AnalyzedVideo(videoId, VideoAnalysisStatus.FAILED, failureCode, List.of(), List.of());
    }

    private static List<String> distinct(List<String> videoIds) {
        return videoIds.stream().distinct().toList();
    }
}
