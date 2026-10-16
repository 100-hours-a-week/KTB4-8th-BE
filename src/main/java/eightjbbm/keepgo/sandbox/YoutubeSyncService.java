package eightjbbm.keepgo.sandbox;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.aiv2.AiServerService;
import eightjbbm.keepgo.sandbox.aiv2.AnalyzeVideoResponseV2;
import eightjbbm.keepgo.sandbox.ds.Address;
import eightjbbm.keepgo.sandbox.ds.VideoId;
import eightjbbm.keepgo.sandbox.ds.VideoIds;
import eightjbbm.keepgo.sandbox.memberlike.MemberLikedVideo;
import eightjbbm.keepgo.sandbox.memberlike.MemberLikedVideoRepository;
import eightjbbm.keepgo.sandbox.videoanalysis.*;
import eightjbbm.keepgo.sandbox.youtubeanalyze.YoutubeApiServiceV2;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class YoutubeSyncService {

    private final MemberRepository memberRepository;
    private final YoutubeApiServiceV2 youtubeApiService;
    private final MemberLikedVideoRepository memberLikedVideoRepository;
    private final VideoAnalysisRepository videoAnalysisRepository;
    private final AiServerService aiServerService;
    private final OutingGuideRepository outingGuideRepository;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;
    private final VideoAnalysisResultRepository videoAnalysisResultRepository;

    // 좋아요 영상 조회 모듈
    public void getLikedVideos(Long memberId) {
        String pageToken = null;
        VideoIds memberLikedVideoIds = VideoIds.fromVideoIds(memberLikedVideoRepository.findAllByMemberId(memberId));

        while (true) {
            var response = youtubeApiService.getVideoLists(memberId, pageToken);
            // 시스템에서 관리하는 좋아요 목록과 대조, 최신화되지 못한 영상 목록 추출
            List<Video> unsyncedVideos = response.getItems().stream()
                    .filter(video -> !memberLikedVideoIds.contains(video.getId()))
                    .toList();

            // processVideos 요청
            // (논블로킹)
            processVideos(memberId, unsyncedVideos);

            // 유튜브 API 응답 본문에 nextPageToken이 존재하지 않을 시(= null일 시): 다음 페이지가 존재하지 않으므로 조회를 중지한다
            pageToken = response.getNextPageToken();
            if (pageToken == null) {
                break;
            }
        }
    }

    // 최신화 필요 영상 처리 모듈
    public void processVideos(Long memberId, List<Video> videos) {
        if (videos.isEmpty()) {
            return;
        }
        // 좋아요 내역 최신화
        Member member = memberRepository.getReferenceById(memberId);
        List<MemberLikedVideo> newLikedVideos = videos.stream()
                .map(
                        video -> new MemberLikedVideo(
                                video.getId(),
                                member
                        )
                )
                .toList();
        memberLikedVideoRepository.saveAll(newLikedVideos);

        // 1. 3분을 넘는가?: TOO_LONG의 분석 결과 저장
        //
        // 시스템과 동기화되지 않은 영상 목록을 3분을 넘는 지의 여부로 분할 (true, false)
        Map<Boolean, List<Video>> videosIfTooLong = videos.stream()
                .collect(Collectors.partitioningBy(
                        video ->
                                Duration.parse(video.getContentDetails().getDuration())
                                .compareTo(Duration.ofMinutes(3))
                                        > 0
                        )
                );
        List<Video> tooLongVideos = videosIfTooLong.get(true);
        List<Video> notTooLongVideos = videosIfTooLong.get(false);
        // 3분을 넘는 영상은 분석 대상이 아니므로 TOO_LONG의 결과로 분석 종료, 결과 기록
        List<VideoAnalysis> tooLongVideoAnalyses = tooLongVideos.stream()
                .map(video -> new VideoAnalysis(
                        video.getId(),
                        VideoAnalysisStatus.TOO_LONG
                ))
                .toList();
        videoAnalysisRepository.saveAll(tooLongVideoAnalyses);
        if (notTooLongVideos.isEmpty()) {
            return;
        }

        // 2. 서비스에 기존 분석 이력이 존재하는가?
        //
        // 3분을 넘지 않는 영상 목록에서 기존에 분석 이력이 존재하는 영상 목록 추출
        Set<String> alreadyAnalyzedVideos = new HashSet<>(
                videoAnalysisRepository.findExistingVideoIds(
                        notTooLongVideos.stream()
                                .map(Video::getId)
                                .toList()
                )
        );
        // 3분을 넘지 않는 영상 목록을 기존 분석 이력 존재 여부로 분할 (true, false)
        Map<Boolean, List<Video>> videosIfAlreadyAnalyzed = notTooLongVideos.stream()
                .collect(Collectors.partitioningBy(
                        video -> alreadyAnalyzedVideos.contains(video.getId())
                ));
        List<Video> alreadyAnalyzedNotTooLongVideos = videosIfAlreadyAnalyzed.get(true);
        List<Video> unanalyzedNotTooLongVideos = videosIfAlreadyAnalyzed.get(false);

        // 기존 분석 이력이 존재하는 영상이 존재할 경우
        if (!alreadyAnalyzedNotTooLongVideos.isEmpty()) {
            // 기존 분석 이력이 존재하는 영상들의 분석 결과 조회
            List<VideoAnalysis> videoAnalyses = videoAnalysisRepository.findAllLatestByVideoId(
                    VideoIds.fromVideos(alreadyAnalyzedNotTooLongVideos)
                            .getVideoIds()
            );

            // 기존 분석 이력이 존재하는 영상들을 분석 결과로 분할 (SUCCESS, NOT_FOUND, FAILED)
            var alreadyAnalyzedVideosByStatus = videoAnalyses.stream()
                    .collect(Collectors.groupingBy(
                            VideoAnalysis::getStatus
                    ));
            List<VideoAnalysis> successAnalyses = alreadyAnalyzedVideosByStatus.getOrDefault(VideoAnalysisStatus.SUCCESS, List.of());
            List<VideoAnalysis> failedAnalyses = alreadyAnalyzedVideosByStatus.getOrDefault(VideoAnalysisStatus.FAILED, List.of());

            // 2-1: SUCCESS - 분석에 성공하여 장소나 행사 정보를 추출한 이력이 있는 영상일 경우
            // 분석 결과에서 장소 및 행사 정보를 추출
            if (!successAnalyses.isEmpty()) {
                Set<OutingGuide> extractedOutingGuides = videoAnalysisResultRepository.findOutingGuidesByVideoAnalysisIdIn(
                        successAnalyses.stream()
                                .map(VideoAnalysis::getId)
                                .collect(Collectors.toSet())
                );
                // 추출된 장소 및 행사 정보 중 기존에 위시리스트에 등록되지 않은 것들을 회원의 위시리스트와 연결
                List<OutingGuide> registeredOutingGuides = outingCollectionPrivateRepository.findAllByMemberIdAndOutingGuides(
                                memberId,
                                extractedOutingGuides
                        ).stream()
                        .map(OutingCollectionPrivate::getOutingGuide)
                        .toList();
                List<OutingCollectionPrivate> wishlistEntries = extractedOutingGuides.stream()
                        .filter(outingGuide -> !registeredOutingGuides.contains(outingGuide))
                        .map(outingGuide -> new OutingCollectionPrivate(member, outingGuide))
                        .toList();
                outingCollectionPrivateRepository.saveAll(wishlistEntries);
            }
            // 2-2: FAILED - 분석에 실패하여 AI 서버 측에 재시도를 요청해야 하는 경우
            // 분석 이력에서 분석에 실패한 영상 ID 추출
            Set<String> failedVideoIds = failedAnalyses.stream()
                    .map(VideoAnalysis::getVideoId)
                    .map(VideoId::videoId)
                    .collect(Collectors.toSet());
            // AI 서버에 전달하기 위해 List<Video>에서 분석에 실패한 영상 ID가 가리키는 Video 객체의 목록 추출
            List<Video> failedVideos = notTooLongVideos.stream()
                    .filter(video -> failedVideoIds.contains(video.getId()))
                    .toList();
            // AI 서버에게 분석 재시도 요청
            // (논블로킹)
            processAnalysisResult(memberId, failedVideos);
            // 2-3: NOT_FOUND - 분석에 성공했으나 분석 결과 추출할 장소나 행사 정보가 없는 영상으로 판정된 경우
            // 추가로 처리해야 할 작업 없음
        }

        // 3. 길이가 3분을 넘지 않으며 기존 분석 이력이 존재하지 않는 영상들을 AI 서버로 전달하여 분석 요청
        // (논블로킹)
        processAnalysisResult(memberId, unanalyzedNotTooLongVideos);
    }

    // AI 분석 요청 / 결과 처리 모듈
    public void processAnalysisResult(Long memberId, List<Video> videos) {
        for (int i = 0; i < videos.size(); i += 10) {
            // 10개 단위로 분할
            List<Video> subList = videos.subList(i, Math.min(i + 10, videos.size()));
            // AI 서버에게 분석 요청 전송
            var response = aiServerService.analyzeVideoV2(
                    subList.stream()
                            .map(Video::getId)
                            .toList()
            );

            // 분석 결과 처리
            if (response instanceof AnalyzeVideoResponseV2.Success successResponse) {
                Member member = memberRepository.getReferenceById(memberId);
                var resultsByStatus = successResponse.data().results().stream()
                        .collect(Collectors.groupingBy(
                                AnalyzeVideoResponseV2.Success.Result::status
                        ));
                var failedResults = resultsByStatus.getOrDefault("failed", List.of());
                var notFoundResults = resultsByStatus.getOrDefault("not_found", List.of());
                var successResults = resultsByStatus.getOrDefault("success", List.of());
                // failed: FAILED의 분석 결과 저장
                List<VideoAnalysis> failed = failedResults.stream()
                        .map(result -> new VideoAnalysis(
                                VideoId.fromUrl(result.videoUrl()).videoId(),
                                VideoAnalysisStatus.FAILED
                        ))
                        .toList();
                videoAnalysisRepository.saveAll(failed);
                // not_found: NOT_FOUND의 분석 결과 저장
                List<VideoAnalysis> notFound = notFoundResults.stream()
                        .map(result -> new VideoAnalysis(
                                VideoId.fromUrl(result.videoUrl()).videoId(),
                                VideoAnalysisStatus.NOT_FOUND
                        ))
                        .toList();
                videoAnalysisRepository.saveAll(notFound);
                // success:
                List<VideoAnalysis> successAnalysis = new ArrayList<>();
                List<VideoAnalysisResult> successAnalysisResults = new ArrayList<>();
                List<OutingGuide> outingGuides = new ArrayList<>();
                List<OutingCollectionPrivate> outingCollectionPrivates = new ArrayList<>();

                List<Address> extractedAddresses = successResults.stream()
                        .map(AnalyzeVideoResponseV2.Success.Result::address)
                        .map(Address::new)
                        .toList();

                Map<Address, OutingGuide> guideByAddress = extractedAddresses.isEmpty()
                        ? new HashMap<>()
                        : outingGuideRepository.findAllByAddresses(extractedAddresses).stream()
                        .collect(
                                Collectors.toMap(
                                        OutingGuide::getAddress,
                                        Function.identity()
                                )
                        );

                Set<Address> wishlistAddresses = guideByAddress.isEmpty()
                        ? new HashSet<>()
                        : new HashSet<>(
                                outingCollectionPrivateRepository.findWishlistAddresses(
                                        memberId,
                                        guideByAddress.values()
                                )
                );

                // 같은 영상에서 추출된 장소 및 행사 정보들을 하나의 VideoAnalysis로 모으기 위해 영상 주소로 분석에 성공한 영상 목록 분할
                var successGroupedByVideoUrl = successResults.stream()
                        .collect(Collectors.groupingBy(AnalyzeVideoResponseV2.Success.Result::videoUrl));

                for (String videoUrl: successGroupedByVideoUrl.keySet()) {
                    // 영상 주소에서 영상 ID 추출
                    String videoId = VideoId.fromUrl(videoUrl).videoId();
                    // SUCCESS의 분석 결과 저장
                    VideoAnalysis videoAnalysis = new VideoAnalysis(videoId, VideoAnalysisStatus.SUCCESS);
                    successAnalysis.add(videoAnalysis);
                    for (AnalyzeVideoResponseV2.Success.Result result : successGroupedByVideoUrl.get(videoUrl)) {
                        Address address = new Address(result.address());
                        // 추출된 정보가 가리키는 장소 및 행사가 기존에 시스템에 등록 이력이 없는 경우 엔티티 생성
                        OutingGuide outingGuide =
                                guideByAddress.computeIfAbsent(address, ignored -> {
                                    OutingGuide created =
                                            OutingGuideFactory.createOutingGuide(result);

                                    outingGuides.add(created);
                                    return created;
                                });
                        // 추출된 정보가 가리키는 장소 및 행사가 이미 회원의 여행 위시리스트에 등록되어 있지 않은 경우 엔티티 생성
                        if (wishlistAddresses.add(address)) {
                            outingCollectionPrivates.add(new OutingCollectionPrivate(member, outingGuide));
                        }
                        // 분석 결과 추출된 장소 및 행사 정보 저장
                        successAnalysisResults.add(new VideoAnalysisResult(videoAnalysis, outingGuide));
                    }
                }

                videoAnalysisRepository.saveAll(successAnalysis);
                outingGuideRepository.saveAll(outingGuides);
                outingCollectionPrivateRepository.saveAll(outingCollectionPrivates);
                videoAnalysisResultRepository.saveAll(successAnalysisResults);

            } else {
                throw new ServiceUnavailableException();
            }
        }
    }
}
