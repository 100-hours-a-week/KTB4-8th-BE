package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.embedding.service.EmbeddingOutboxService;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysis;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisRepository;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisResult;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisResultRepository;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncRepository;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.GuideKey;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistSyncService;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 배치 하나의 결과를 트랜잭션 하나로 저장한다.
///
/// 가이드 찾기·생성, 분석 이력과 매핑, 위시리스트 반영, 동기화 진척도 갱신을 같은 트랜잭션에
/// 담는다. 진척도 갱신은 실행 인스턴스와 RUNNING 상태를 조건으로 하므로, heartbeat가 만료된
/// 이전 실행자의 커밋은 영향받은 행이 0개가 되어 롤백된다.
@Service
@RequiredArgsConstructor
public class YoutubeSyncBatchWriter {

    private final YoutubeSyncRepository syncRepository;
    private final VideoAnalysisRepository videoAnalysisRepository;
    private final VideoAnalysisResultRepository videoAnalysisResultRepository;
    private final OutingGuideRepository outingGuideRepository;
    private final OutingWishlistSyncService outingWishlistSyncService;
    private final EmbeddingOutboxService embeddingOutboxService;

    /// 배치 결과를 저장한다. 새로 만든 가이드의 임베딩 작업도 같은 트랜잭션에 기록한다.
    @Transactional
    public void commit(Long syncId, String instanceId, Long memberId, BatchAnalysis batch, int progress) {
        fence(syncId, instanceId, batch.processedCount(), batch.failedCount(), progress);

        List<OutingGuide> createdGuides = new ArrayList<>();
        Map<GuideKey, OutingGuide> guidesByKey = resolveGuides(batch.guides(), createdGuides);

        List<VideoAnalysis> analyses =
                batch.videos().stream().map(video -> analysis(video, syncId)).toList();
        videoAnalysisRepository.saveAll(analyses);

        Map<String, VideoAnalysis> analysesByVideoId =
                analyses.stream().collect(Collectors.toMap(VideoAnalysis::getVideoId, Function.identity()));
        List<VideoAnalysisResult> mappings = mappings(batch, analysesByVideoId, guidesByKey);
        videoAnalysisResultRepository.saveAll(mappings);

        outingWishlistSyncService.sync(memberId, List.copyOf(guidesByKey.values()));
        embeddingOutboxService.enqueue(createdGuides);
    }

    /// 커밋에 실패한 배치를 실패 영상으로만 기록한다. 이미 커밋된 배치는 건드리지 않는다.
    @Transactional
    public void recordFailed(Long syncId, String instanceId, List<String> videoIds, int progress) {
        fence(syncId, instanceId, videoIds.size(), videoIds.size(), progress);

        List<VideoAnalysis> analyses = videoIds.stream()
                .distinct()
                .map(videoId -> {
                    VideoAnalysis analysis = new VideoAnalysis(videoId, syncId);
                    analysis.markFailed();
                    return analysis;
                })
                .toList();
        videoAnalysisRepository.saveAll(analyses);
    }

    private void fence(Long syncId, String instanceId, int processedCount, int failedCount, int progress) {
        int updated = syncRepository.recordBatchFenced(
                syncId, instanceId, processedCount, failedCount, progress, Instant.now());
        if (updated == 0) {
            throw new SyncFencedException(syncId);
        }
    }

    private VideoAnalysis analysis(BatchAnalysis.AnalyzedVideo video, Long syncId) {
        VideoAnalysis analysis = new VideoAnalysis(video.videoId(), syncId);
        switch (video.status()) {
            case SUCCESS -> analysis.markSuccess();
            case NOT_FOUND -> analysis.markNotFound();
            case FAILED -> analysis.markFailed();
            default -> throw new IllegalArgumentException("지원하지 않는 분석 상태입니다: " + video.status());
        }
        return analysis;
    }

    /// 같은 배치 안의 중복 가이드를 하나로 합치고, GuideKey 순서로 찾기·생성을 고정한다.
    private Map<GuideKey, OutingGuide> resolveGuides(List<OutingGuide> guides, List<OutingGuide> createdGuides) {
        Map<GuideKey, OutingGuide> unique = new LinkedHashMap<>();
        for (OutingGuide guide : guides) {
            unique.putIfAbsent(keyOf(guide), guide);
        }
        if (unique.isEmpty()) {
            return Map.of();
        }

        var addresses = unique.values().stream()
                .map(OutingGuide::getAddress)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<GuideKey, OutingGuide> existing = addresses.isEmpty()
                ? Map.of()
                : outingGuideRepository.findAllByAddresses(addresses).stream()
                        .collect(Collectors.toMap(
                                YoutubeSyncBatchWriter::keyOf, Function.identity(), (first, duplicate) -> first));

        Map<GuideKey, OutingGuide> resolved = new LinkedHashMap<>();
        for (GuideKey key : sorted(unique.keySet())) {
            OutingGuide guide = existing.get(key);
            if (guide == null) {
                guide = outingGuideRepository.save(unique.get(key));
                createdGuides.add(guide);
            }
            resolved.put(key, guide);
        }
        return resolved;
    }

    private List<VideoAnalysisResult> mappings(
            BatchAnalysis batch, Map<String, VideoAnalysis> analysesByVideoId, Map<GuideKey, OutingGuide> guidesByKey) {
        Map<String, Set<GuideKey>> mappedKeysByVideo = new HashMap<>();
        List<VideoAnalysisResult> mappings = new ArrayList<>();

        for (BatchAnalysis.AnalyzedVideo video : batch.videos()) {
            VideoAnalysis analysis = analysesByVideoId.get(video.videoId());
            Set<GuideKey> mappedKeys = mappedKeysByVideo.computeIfAbsent(video.videoId(), key -> new HashSet<>());

            for (OutingGuide guide : video.guides()) {
                GuideKey key = keyOf(guide);
                if (mappedKeys.add(key)) {
                    mappings.add(new VideoAnalysisResult(analysis, guidesByKey.get(key)));
                }
            }
        }
        return mappings;
    }

    private static List<GuideKey> sorted(Set<GuideKey> keys) {
        return keys.stream()
                .sorted(Comparator.comparing(GuideKey::name, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(
                                key -> key.address() == null
                                        ? null
                                        : key.address().address(),
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private static GuideKey keyOf(OutingGuide guide) {
        return new GuideKey(guide.getName(), guide.getAddress());
    }
}
