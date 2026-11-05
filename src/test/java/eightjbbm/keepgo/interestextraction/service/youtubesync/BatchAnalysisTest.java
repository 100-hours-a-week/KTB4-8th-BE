package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.ExcludedCandidate;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse.GuideType;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse.Status;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AI 배치 응답 정리")
class BatchAnalysisTest {

    @Test
    @DisplayName("영상 하나의 여러 가이드를 모두 보존한다")
    void keepsAllGuidesOfOneVideo() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0"),
                response(success(
                        "v0",
                        guide(GuideType.PLACE, "첫 장소", "서울특별시 종로구 첫로 1"),
                        guide(GuideType.EVENT, "둘째 행사", "서울특별시 종로구 둘째로 2"))));

        assertThat(analysis.videos().getFirst().status()).isEqualTo(VideoAnalysisStatus.SUCCESS);
        assertThat(analysis.guides()).hasSize(2);
        assertThat(analysis.guides()).extracting(OutingGuide::getName).containsExactly("첫 장소", "둘째 행사");
        assertThat(analysis.videos().getFirst().excludedCandidates()).isEmpty();
    }

    @Test
    @DisplayName("이름과 주소가 같아도 유형이 다르면 서로 다른 가이드로 남긴다")
    void keepsSameNameAndAddressWhenTypeDiffers() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0"),
                response(success(
                        "v0", guide(GuideType.PLACE, "같은 이름", "같은 주소"), guide(GuideType.EVENT, "같은 이름", "같은 주소"))));

        assertThat(analysis.guides()).hasSize(2);
        assertThat(analysis.guides().get(0)).isInstanceOf(OutingPlace.class);
        assertThat(analysis.guides().get(1)).isInstanceOf(OutingEvent.class);
    }

    @Test
    @DisplayName("저장할 수 없는 후보는 위치와 사유를 남기고 나머지 후보는 유지한다")
    void excludesInvalidCandidateAndKeepsValidOnes() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0"),
                response(success(
                        "v0",
                        guide(GuideType.PLACE, "정상 장소", "서울특별시 종로구 정상로 1"),
                        guide(GuideType.PLACE, " ", "서울특별시 종로구 공백로 2"),
                        guide(GuideType.PLACE, "주소 없는 장소", null))));

        var video = analysis.videos().getFirst();
        assertThat(video.status()).isEqualTo(VideoAnalysisStatus.SUCCESS);
        assertThat(video.guides()).extracting(OutingGuide::getName).containsExactly("정상 장소");
        assertThat(video.excludedCandidates())
                .containsExactly(new ExcludedCandidate(1, "NAME_MISSING"), new ExcludedCandidate(2, "ADDRESS_MISSING"));
        assertThat(analysis.failedCount()).isZero();
    }

    @Test
    @DisplayName("모든 후보가 제외된 영상은 INVALID_GUIDE_DATA로 실패한다")
    void failsWhenAllCandidatesAreExcluded() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0"),
                response(success(
                        "v0", guide(GuideType.PLACE, null, "서울특별시 종로구 주소로 1"), guide(GuideType.EVENT, "행사", null))));

        var video = analysis.videos().getFirst();
        assertThat(video.status()).isEqualTo(VideoAnalysisStatus.FAILED);
        assertThat(video.failureCode()).isEqualTo(BatchAnalysis.INVALID_GUIDE_DATA);
        assertThat(video.guides()).isEmpty();
        assertThat(video.excludedCandidates()).hasSize(2);
        assertThat(analysis.failedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("알 수 없는 상태는 그 영상만 실패로 만들고 다른 영상은 유지한다")
    void failsOnlyUnknownStatusVideo() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0", "v1"),
                response(
                        new VideoAnalysisResponse.Result("v0", Status.UNKNOWN, null, List.of()),
                        success("v1", guide(GuideType.PLACE, "정상 장소", "서울특별시 종로구 정상로 1"))));

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::status)
                .containsExactly(VideoAnalysisStatus.FAILED, VideoAnalysisStatus.SUCCESS);
        assertThat(analysis.videos().getFirst().failureCode()).isEqualTo(BatchAnalysis.INVALID_AI_RESPONSE);
        assertThat(analysis.failedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("알 수 없는 유형이 있는 영상은 그 영상만 실패로 만든다")
    void failsOnlyUnknownGuideTypeVideo() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0", "v1"),
                response(
                        success("v0", guide(GuideType.UNKNOWN, "알 수 없는 유형", "서울특별시 종로구 기타로 1")),
                        success("v1", guide(GuideType.PLACE, "정상 장소", "서울특별시 종로구 정상로 1"))));

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::status)
                .containsExactly(VideoAnalysisStatus.FAILED, VideoAnalysisStatus.SUCCESS);
        assertThat(analysis.videos().getFirst().failureCode()).isEqualTo(BatchAnalysis.INVALID_AI_RESPONSE);
    }

    @Test
    @DisplayName("not_found·failed 상태를 그대로 옮기고 failed의 AI 오류 코드를 남긴다")
    void mapsUnsuccessfulStatuses() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0", "v1"),
                response(
                        new VideoAnalysisResponse.Result("v0", Status.NOT_FOUND, null, List.of()),
                        new VideoAnalysisResponse.Result(
                                "v1",
                                Status.FAILED,
                                new VideoAnalysisResponse.Failure("llm_timeout", null),
                                List.of())));

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::status)
                .containsExactly(VideoAnalysisStatus.NOT_FOUND, VideoAnalysisStatus.FAILED);
        assertThat(analysis.videos().get(1).failureCode()).isEqualTo("llm_timeout");
    }

    @Test
    @DisplayName("저장 한도보다 긴 AI 오류 코드는 INVALID_AI_RESPONSE로 바꾼다")
    void replacesTooLongFailureCode() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0", "v1"),
                response(
                        new VideoAnalysisResponse.Result(
                                "v0",
                                Status.FAILED,
                                new VideoAnalysisResponse.Failure("e".repeat(64), null),
                                List.of()),
                        new VideoAnalysisResponse.Result(
                                "v1",
                                Status.FAILED,
                                new VideoAnalysisResponse.Failure("e".repeat(65), null),
                                List.of())));

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::failureCode)
                .containsExactly("e".repeat(64), BatchAnalysis.INVALID_AI_RESPONSE);
    }

    @Test
    @DisplayName("응답에 없는 영상은 INVALID_AI_RESPONSE로 실패한다")
    void failsMissingVideo() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0", "v1"), response(success("v0", guide(GuideType.PLACE, "정상 장소", "서울특별시 종로구 정상로 1"))));

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::status)
                .containsExactly(VideoAnalysisStatus.SUCCESS, VideoAnalysisStatus.FAILED);
        assertThat(analysis.videos().get(1).failureCode()).isEqualTo(BatchAnalysis.INVALID_AI_RESPONSE);
    }

    @Test
    @DisplayName("성공 응답이라도 가이드가 없으면 INVALID_AI_RESPONSE로 실패한다")
    void failsSuccessWithoutGuides() {
        BatchAnalysis analysis = BatchAnalysis.from(List.of("v0"), response(success("v0")));

        assertThat(analysis.videos().getFirst().status()).isEqualTo(VideoAnalysisStatus.FAILED);
        assertThat(analysis.videos().getFirst().failureCode()).isEqualTo(BatchAnalysis.INVALID_AI_RESPONSE);
    }

    @Test
    @DisplayName("AI 호출이 실패한 배치는 모든 영상을 지정한 실패 코드로 만든다")
    void marksWholeBatchFailed() {
        BatchAnalysis analysis = BatchAnalysis.failed(List.of("v0", "v1"), "TIMEOUT");

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::status)
                .containsOnly(VideoAnalysisStatus.FAILED);
        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::failureCode)
                .containsOnly("TIMEOUT");
        assertThat(analysis.failedCount()).isEqualTo(2);
    }

    private static VideoAnalysisResponse response(VideoAnalysisResponse.Result... results) {
        return new VideoAnalysisResponse(List.of(results));
    }

    private static VideoAnalysisResponse.Result success(String videoId, VideoAnalysisResponse.Guide... guides) {
        return new VideoAnalysisResponse.Result(videoId, Status.SUCCESS, null, List.of(guides));
    }

    private static VideoAnalysisResponse.Guide guide(GuideType type, String name, String address) {
        return new VideoAnalysisResponse.Guide(
                type,
                name,
                address,
                37.5,
                127.0,
                "요약",
                "상세",
                "카페",
                null,
                type == GuideType.EVENT ? LocalDate.of(2026, 10, 1) : null,
                type == GuideType.EVENT ? LocalDate.of(2026, 10, 31) : null,
                false,
                0.9);
    }
}
