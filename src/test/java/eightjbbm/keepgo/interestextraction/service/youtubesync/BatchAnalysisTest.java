package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AI 배치 응답 정리")
class BatchAnalysisTest {

    @Test
    @DisplayName("성공한 영상은 가이드와 SUCCESS 상태로 정리한다")
    void mapsSuccessfulResultToGuide() {
        BatchAnalysis analysis =
                BatchAnalysis.from(List.of("v0"), success(result("v0", "success", "장소", "스텁 장소", "서울특별시 종로구 스텁로 1")));

        assertThat(analysis.processedCount()).isEqualTo(1);
        assertThat(analysis.failedCount()).isZero();
        assertThat(analysis.videos().getFirst().status()).isEqualTo(VideoAnalysisStatus.SUCCESS);
        assertThat(analysis.guides()).hasSize(1);
        assertThat(analysis.guides().getFirst().getName()).isEqualTo("스텁 장소");
        assertThat(analysis.guides().getFirst().getAddress().address()).isEqualTo("서울특별시 종로구 스텁로 1");
    }

    @Test
    @DisplayName("not_found·failed·알 수 없는 상태는 해당 영상만 실패로 정리한다")
    void marksEachUnsuccessfulStatus() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0", "v1", "v2", "v3"),
                success(
                        result("v0", "not_found", "장소", null, null),
                        result("v1", "failed", "장소", null, null),
                        result("v2", "unknown", "장소", null, null),
                        result("v3", "success", "장소", "스텁 장소", "서울특별시 종로구 스텁로 3")));

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::status)
                .containsExactly(
                        VideoAnalysisStatus.NOT_FOUND,
                        VideoAnalysisStatus.FAILED,
                        VideoAnalysisStatus.FAILED,
                        VideoAnalysisStatus.SUCCESS);
        assertThat(analysis.failedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("응답에 없는 영상과 요청하지 않은 영상은 실패로 남기고 무시한다")
    void marksMissingResultsAsFailedAndIgnoresUnknownIds() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0", "v1"),
                success(
                        result("v0", "success", "장소", "스텁 장소", "서울특별시 종로구 스텁로 0"),
                        result("v9", "success", "장소", "다른 장소", "서울특별시 종로구 다른로 9")));

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::videoId)
                .containsExactly("v0", "v1");
        assertThat(analysis.videos().get(1).status()).isEqualTo(VideoAnalysisStatus.FAILED);
        assertThat(analysis.guides()).hasSize(1);
    }

    @Test
    @DisplayName("같은 영상의 중복 결과는 첫 번째만 반영한다")
    void ignoresDuplicateResults() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0"),
                success(
                        result("v0", "success", "장소", "첫 장소", "서울특별시 종로구 첫로 1"),
                        result("v0", "failed", "장소", null, null)));

        assertThat(analysis.videos().getFirst().status()).isEqualTo(VideoAnalysisStatus.SUCCESS);
        assertThat(analysis.guides()).hasSize(1);
    }

    @Test
    @DisplayName("AI 호출이 실패한 배치는 모든 영상을 FAILED로 만든다")
    void marksWholeBatchFailedOnFailureResponse() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0", "v1"),
                new AnalyzeVideoResponseV2.Failure("failure", new AnalyzeVideoResponseV2.Failure.Data()));

        assertThat(analysis.videos())
                .extracting(BatchAnalysis.AnalyzedVideo::status)
                .containsOnly(VideoAnalysisStatus.FAILED);
        assertThat(analysis.failedCount()).isEqualTo(2);
        assertThat(analysis.guides()).isEmpty();
    }

    @Test
    @DisplayName("shorts URL이 아닌 결과는 매칭하지 않는다")
    void ignoresNonShortsUrls() {
        BatchAnalysis analysis = BatchAnalysis.from(
                List.of("v0"), success(result("https://example.com/v0", "success", "장소", "장소", "주소")));

        assertThat(analysis.videos().getFirst().status()).isEqualTo(VideoAnalysisStatus.FAILED);
        assertThat(analysis.guides()).isEmpty();
    }

    private AnalyzeVideoResponseV2.Success success(AnalyzeVideoResponseV2.Success.Result... results) {
        return new AnalyzeVideoResponseV2.Success("ok", new AnalyzeVideoResponseV2.Success.Data(List.of(results)));
    }

    private AnalyzeVideoResponseV2.Success.Result result(
            String videoId, String status, String category, String name, String address) {
        return new AnalyzeVideoResponseV2.Success.Result(
                "https://youtube.com/shorts/" + videoId,
                status,
                null,
                name,
                address,
                null,
                category,
                null,
                null,
                null,
                null,
                37.5f,
                127.0f,
                0.9f,
                "요약");
    }
}
