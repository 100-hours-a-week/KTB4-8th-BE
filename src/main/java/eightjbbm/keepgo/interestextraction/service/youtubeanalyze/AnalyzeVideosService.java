package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.OutingGuideFactory;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import eightjbbm.keepgo.sandbox.aiv2.AiServerService;
import eightjbbm.keepgo.sandbox.aiv2.AnalyzeVideoResponseV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeVideosService {

    private final OutingGuideRepository outingGuideRepository;
    private final AiServerService aiServerService;
    private final YoutubeAnalyzeService youtubeAnalyzeService;
    /// 장소 및 행사 목록 최신화
    ///
    @Transactional
    public List<OutingGuide> analyzeVideos(List<Video> videos) {
        // 분석할 영상들이 비즈니스상 유효한 영상들인지 검증한다.
        //List<Video> videosValidated = validateVideos(videos);
        List<Video> videosValidated = videos;
        log.info("analyzeVideos: videosValidated.size()=" + videosValidated.size());
        // 검증을 통과한 영상들을 분석하여 장소 및 행사 목록을 추출한다.
        // 블로킹 지점 (동기 논블로킹 필요?)
        // 결과는 Transient 객체들이므로, 이걸 실제 엔티티로 바꿔줘야 한다.
        List<OutingGuide> outingGuidesExtracted = analyzeVideosInternal(videosValidated).stream()
                .collect(Collectors.toMap(
                        GuideKey::from,
                        guide -> guide,
                        (first, _) -> first,
                        LinkedHashMap::new
                )).values().stream().toList();
        log.info("analyzeVideos: outingGuidesExtracted.size()=" + outingGuidesExtracted.size());
        // 분석 결과 추출된 장소와 행사를 기존 장소 및 행사 목록과 대조하여 최신화가 필요한 부분을 파악한다.
        // 분석 결과 추출된 장소와 행사 중 기존에 저장된 경우 파악
        // 중복 판단 기준: 주소와 이름이 모두 일치하는 경우
        if (outingGuidesExtracted.isEmpty()) return List.of();
        List<OutingGuide> allByAddresses = new ArrayList<>(outingGuideRepository
                .findAllByAddresses(
                        outingGuidesExtracted.stream()
                                .map(OutingGuide::getAddress)
                                .distinct()
                                .toList()).stream()
                .filter(outingGuide -> {
                    for (var o: outingGuidesExtracted) {
                        if (outingGuide.isSameAs(o)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList());
        // 기존 장소 및 행사 목록에 미처 반영되지 못한 부분을 추가하여 최신화한다.
        List<OutingGuide> newOutingGuides = outingGuideRepository
                .saveAll(outingGuidesExtracted.stream()
                        .filter(outingGuide -> {
                            for (var o: allByAddresses) {
                                if (outingGuide.isSameAs(o)) return false;
                            }
                            return true;
                        })
                        .toList());
        // AI 서버 임베딩 저장 API 호출 필요 (비동기 논블로킹)
        aiServerService.embedPlaces(newOutingGuides);
        // 분석의 결과로써 추출된 장소 및 행사 목록을 반환한다.
        allByAddresses.addAll(newOutingGuides);
        return allByAddresses;
    }

    /// 영상의 비즈니스 유효성을 검사한다.
    ///
    /// 조건: 3분 이내의 업로드된 공개 동영상
    ///
    /// 전략 패턴 적용 가능
    ///
    private List<Video> validateVideos(List<Video> videos) {
        return videos.stream()
                .filter(video ->
                        Duration.parse(video.getContentDetails().getDuration()).compareTo(Duration.ofMinutes(3)) <= 0
                                && "public".equals(video.getStatus().getPrivacyStatus())
                                && "uploaded".equals(video.getStatus().getUploadStatus()))
                .toList();
    }

    private List<OutingGuide> analyzeVideosInternal(List<Video> videos) {
        log.info("analyzeVideosInternal: videos.size()=" + videos.size());
        // 백엔드 측에서 분석할 수 있는 영상이라면 분석한다.
        Map<Video, OutingGuide> videosAnalyzedByBackend = youtubeAnalyzeService.analyzeVideosInternalByBackend(videos);
        // 백엔드 측에서 분석할 수 없는 영상들을 선별한다.
        List<Video> videosNeedToBeAnalyzedByAi = videos.stream()
                .filter(video -> !videosAnalyzedByBackend.containsKey(video))
                .toList();
        // 백엔드 측에서 분석할 수 없는 영상들은 AI 서버에게 분석을 요청한다.
        // 블로킹 지점 (동기 논블로킹 필요?)
        List<OutingGuide> outingGuides = analyzeVideosInternalByAi(videosNeedToBeAnalyzedByAi);
        // 백엔드 측의 분석 결과와 AI 서버 측 분석 결과 추출된 장소 및 행사 정보를 합쳐 반환한다.
        outingGuides.addAll(videosAnalyzedByBackend.values());
        return outingGuides;
    }

    private List<OutingGuide> analyzeVideosInternalByAi(List<Video> videos) {
        log.info("analyzeVideosInternalByAi: videos.size()=" + videos.size());
        List<OutingGuide> result = new ArrayList<>();
        // 10개 단위로 분할
        for (int i = 0; i < videos.size(); i += 10) {
            List<Video> subList = videos.subList(i, Math.min(i + 10, videos.size()));
            // 블로킹 지점 (동기 논블로킹 필요)
            result.addAll(requestAnalysis(subList));
        }
        return result;
    }

    // 전략 패턴 적용 필요
    private List<OutingGuide> requestAnalysis(List<Video> videos) {
        // AI 서버에게 요청을 전송하여, 요청의 결과를 토대로 추출된 장소 및 행사 정보를 반환한다.
        // AI 서버에게 분석 요청 전송
        log.info("requestAnalysis: videos.size()=" + videos.size());
        var response = aiServerService.analyzeVideoV2(
                videos.stream()
                        .map(Video::getId)
                        .toList()
        );
        // 분석이 성공적으로 수행된 경우
        if (response instanceof AnalyzeVideoResponseV2.Success success) {
            return processResult(success);
        } else return List.of();
    }

    // 전략 패턴 적용 가능
    private List<OutingGuide> processResult(AnalyzeVideoResponseV2.Success success) {
        log.info("processResult");
        List<OutingGuide> extractedOutingGuides = new ArrayList<>();
        for (var result: success.data().results()) {
            switch (result.status()) {
                case "success" -> extractedOutingGuides
                        .add(OutingGuideFactory.createOutingGuide(result));
                case "not_found", "failed" -> {}
                default -> throw new IllegalStateException();
            }
        }
        return extractedOutingGuides;
    }
}
