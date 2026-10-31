package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysis;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisRepository;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisResult;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisResultRepository;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 영상 분석 결과를 YouTube 영상 ID 기준으로 시스템 전체에서 공유한다.
///
/// 영상의 상태는 분석 이력 중 하나로 정한다. 진행 중인 분석이 있으면 그 분석에 합류한다(진행 중 행은
/// 영상당 하나만 허용하는 유일 제약이 인스턴스 사이의 소유권을 정한다). 없으면 가장 최근에 끝난 이력을
/// 따른다. 완료 시각이 같으면 ID가 큰 이력이 최근이고, 완료 시각이 없는 이전 계약의 이력은 가장 오래된
/// 것으로 본다. 최근 이력이 현행 계약의 SUCCESS(가이드 매핑 있음)·NOT_FOUND이면 시간 만료 없이 재사용하고,
/// FAILED이거나 완료 시각이 없는 이전 계약의 이력이면 다시 분석한다. 재사용·합류 모두 새 분석 이력을 만들지
/// 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedVideoAnalysisService {

    private static final long POLL_INTERVAL_MILLIS = 200;
    private static final Duration WAIT_MARGIN = Duration.ofSeconds(30);
    private static final String IN_PROGRESS_UNIQUE_KEY = "uk_video_analysis_in_progress_video";

    private static final Comparator<VideoAnalysis> NEWEST_COMPLETED = Comparator.comparing(
                    VideoAnalysis::getCompletedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(VideoAnalysis::getId);

    private final VideoAnalysisRepository videoAnalysisRepository;
    private final VideoAnalysisResultRepository videoAnalysisResultRepository;
    private final VideoAnalysisManager videoAnalysisManager;
    private final AiCallProperties aiCallProperties;

    /// 분석 소유권 경쟁의 결과. 소유하면 호출자가 AI에 보내고, 아니면 재사용하거나 진행 중인 분석에 합류한다.
    public record Claim(boolean owned, SharedVideoResult reused) {

        static Claim won() {
            return new Claim(true, null);
        }

        static Claim reusing(SharedVideoResult result) {
            return new Claim(false, result);
        }

        static Claim joining() {
            return new Claim(false, null);
        }
    }

    private enum State {
        IN_PROGRESS,
        REUSABLE,
        NEEDS_ANALYSIS
    }

    private record Resolution(State state, SharedVideoResult result) {}

    /// 바로 재사용할 수 있는 완료 결과만 돌려준다. 진행 중이거나 다시 분석할 영상은 포함하지 않는다.
    @Transactional(readOnly = true)
    public Map<String, SharedVideoResult> findCompleted(Collection<String> videoIds) {
        Map<String, SharedVideoResult> completed = new LinkedHashMap<>();
        resolve(videoIds, null).forEach((videoId, resolution) -> {
            if (resolution.state() == State.REUSABLE) {
                completed.put(videoId, resolution.result());
            }
        });
        return completed;
    }

    /// 영상의 분석 소유권을 얻는다. AI에 보내기 직전에 호출한다.
    ///
    /// 진행 중 행 삽입이 진행 중 분석의 유일 제약에 막히면 다른 곳이 분석 중이므로 합류한다. 다른 무결성
    /// 위반은 합류로 가리지 않고 그대로 던진다. 삽입에 성공해도 그 사이 다른 동기화가 유효한 결과를
    /// 끝냈다면 소유권을 내려놓고 그 결과를 재사용한다. 삽입이 실패하는 경우를 잡아야 하므로 이 메서드는
    /// 트랜잭션 없이 저장소 호출마다 커밋한다.
    public Claim claim(Long syncId, String videoId) {
        VideoAnalysis claimed;
        try {
            claimed = videoAnalysisRepository.saveAndFlush(new VideoAnalysis(videoId, syncId));
        } catch (DataIntegrityViolationException violation) {
            if (!violatesInProgressUniqueKey(violation)) {
                throw violation;
            }
            return Claim.joining();
        }

        Resolution resolution = resolve(List.of(videoId), claimed.getId()).get(videoId);
        if (resolution.state() == State.REUSABLE) {
            videoAnalysisRepository.delete(claimed);
            return Claim.reusing(resolution.result());
        }
        return Claim.won();
    }

    private static boolean violatesInProgressUniqueKey(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(IN_PROGRESS_UNIQUE_KEY)) {
                return true;
            }
        }
        return false;
    }

    /// 합류한 영상의 분석이 끝날 때까지 기다린다. 끝난 분석이 실패했거나 다시 분석할 상태면 FAILED로 돌려준다.
    ///
    /// 소유한 동기화가 이미 끝났는데 진행 중으로 남은 분석은 실패로 마감해 기다림이 끝나게 한다. 실행 인스턴스가
    /// 죽은 경우는 동기화 heartbeat 만료로 sweeper가 분석을 FAILED로 마감한다. 소유자는 살아 있는데 분석이
    /// 끝나지 않는 경우를 위해 전송 제한 시간과 슬롯 임대 시간을 합한 만큼만 기다리고, 넘으면 남은 영상을
    /// FAILED로 돌려준다.
    public Map<String, SharedVideoResult> awaitResolved(Collection<String> videoIds) throws InterruptedException {
        return awaitResolved(
                videoIds,
                aiCallProperties
                        .analysisTimeout()
                        .plus(aiCallProperties.leaseDuration())
                        .plus(WAIT_MARGIN));
    }

    /// 최대 `maxWait`까지만 기다린다. 넘으면 아직 진행 중인 영상은 이 분석 이력을 바꾸지 않고 FAILED로 돌려준다.
    public Map<String, SharedVideoResult> awaitResolved(Collection<String> videoIds, Duration maxWait)
            throws InterruptedException {
        long deadline = System.nanoTime() + maxWait.toNanos();
        Map<String, SharedVideoResult> resolved = new LinkedHashMap<>();
        List<String> waiting = new ArrayList<>(videoIds);
        while (true) {
            failOrphans(waiting);
            Map<String, Resolution> resolutions = resolve(waiting, null);
            for (String videoId : List.copyOf(waiting)) {
                Resolution resolution = resolutions.get(videoId);
                if (resolution.state() != State.IN_PROGRESS) {
                    resolved.put(
                            videoId,
                            resolution.state() == State.REUSABLE
                                    ? resolution.result()
                                    : SharedVideoResult.failed(videoId));
                    waiting.remove(videoId);
                }
            }
            if (waiting.isEmpty()) {
                return resolved;
            }
            if (System.nanoTime() - deadline >= 0) {
                log.warn("합류한 분석이 제한 시간 안에 끝나지 않아 실패로 처리합니다: videoCount={}", waiting.size());
                waiting.forEach(videoId -> resolved.put(videoId, SharedVideoResult.failed(videoId)));
                return resolved;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
    }

    private void failOrphans(Collection<String> videoIds) {
        for (Long syncId : videoAnalysisRepository.findOrphanedSyncIds(videoIds)) {
            log.warn("끝난 동기화에 남은 진행 중 분석을 실패로 마감합니다: syncId={}", syncId);
            videoAnalysisManager.failInProgress(syncId);
        }
    }

    private Map<String, Resolution> resolve(Collection<String> videoIds, Long ignoredAnalysisId) {
        Map<String, List<VideoAnalysis>> historyByVideo = videoAnalysisRepository.findAllByVideoIdIn(videoIds).stream()
                .filter(analysis -> !analysis.getId().equals(ignoredAnalysisId))
                .collect(Collectors.groupingBy(VideoAnalysis::getVideoId));

        Map<String, VideoAnalysis> newestCompleted = new HashMap<>();
        Map<String, Resolution> resolutions = new HashMap<>();
        historyByVideo.forEach((videoId, history) -> {
            if (history.stream().anyMatch(analysis -> analysis.getStatus() == VideoAnalysisStatus.IN_PROGRESS)) {
                resolutions.put(videoId, new Resolution(State.IN_PROGRESS, null));
            } else {
                newestCompleted.put(
                        videoId, history.stream().max(NEWEST_COMPLETED).orElseThrow());
            }
        });

        Map<Long, List<Long>> guideIdsByAnalysis = guideIdsByAnalysis(newestCompleted.values());
        for (String videoId : videoIds) {
            resolutions.computeIfAbsent(
                    videoId, id -> reusableOrReanalyze(newestCompleted.get(id), guideIdsByAnalysis));
        }
        return resolutions;
    }

    private Resolution reusableOrReanalyze(VideoAnalysis newest, Map<Long, List<Long>> guideIdsByAnalysis) {
        if (newest == null || newest.getCompletedAt() == null) {
            return new Resolution(State.NEEDS_ANALYSIS, null);
        }
        String videoId = newest.getVideoId();
        List<Long> guideIds = guideIdsByAnalysis.getOrDefault(newest.getId(), List.of());
        return switch (newest.getStatus()) {
            case NOT_FOUND ->
                new Resolution(
                        State.REUSABLE, new SharedVideoResult(videoId, VideoAnalysisStatus.NOT_FOUND, List.of()));
            case SUCCESS ->
                guideIds.isEmpty()
                        ? new Resolution(State.NEEDS_ANALYSIS, null)
                        : new Resolution(
                                State.REUSABLE, new SharedVideoResult(videoId, VideoAnalysisStatus.SUCCESS, guideIds));
            case FAILED, IN_PROGRESS -> new Resolution(State.NEEDS_ANALYSIS, null);
        };
    }

    private Map<Long, List<Long>> guideIdsByAnalysis(Collection<VideoAnalysis> analyses) {
        List<Long> successIds = analyses.stream()
                .filter(analysis -> analysis.getStatus() == VideoAnalysisStatus.SUCCESS)
                .map(VideoAnalysis::getId)
                .toList();
        if (successIds.isEmpty()) {
            return Map.of();
        }
        return videoAnalysisResultRepository.findAllByIdVideoAnalysisIdIn(successIds).stream()
                .map(VideoAnalysisResult::getId)
                .collect(Collectors.groupingBy(
                        id -> id.videoAnalysisId(), Collectors.mapping(id -> id.outingGuideId(), Collectors.toList())));
    }
}
