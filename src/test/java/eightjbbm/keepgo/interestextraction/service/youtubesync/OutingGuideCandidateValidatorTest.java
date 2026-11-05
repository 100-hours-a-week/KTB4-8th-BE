package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.ExcludedCandidate;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse.GuideType;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("나들이 가이드 후보 검증")
class OutingGuideCandidateValidatorTest {

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidCandidates")
    @DisplayName("저장할 수 없는 후보는 위치와 사유와 함께 제외한다")
    void excludesInvalidCandidate(int index, String reason, VideoAnalysisResponse.Guide guide) {
        var result = OutingGuideCandidateValidator.validate(index, guide);

        assertThat(result.isValid()).isFalse();
        assertThat(result.excluded()).isEqualTo(new ExcludedCandidate(index, reason));
    }

    @Test
    @DisplayName("이름과 주소가 255자이고 좌표가 경계값이면 저장한다")
    void acceptsBoundaryValues() {
        var result = OutingGuideCandidateValidator.validate(
                0, guide(GuideType.PLACE, "가".repeat(255), "나".repeat(255), 90.0, -180.0, null, null));

        assertThat(result.isValid()).isTrue();
        assertThat(result.guide()).isNotNull();
    }

    @Test
    @DisplayName("기간을 모르는 행사는 저장한다")
    void acceptsEventWithoutPeriod() {
        var result = OutingGuideCandidateValidator.validate(
                0, guide(GuideType.EVENT, "행사", "서울특별시 종로구 행사로 1", 37.5, 127.0, null, null));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("행사 기간의 시작일과 종료일이 같으면 저장한다")
    void acceptsEventWithSameStartAndEndDate() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        var result = OutingGuideCandidateValidator.validate(
                0, guide(GuideType.EVENT, "행사", "서울특별시 종로구 행사로 1", 37.5, 127.0, date, date));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("알 수 없는 유형은 저장하지 않는다")
    void excludesUnknownType() {
        var result = OutingGuideCandidateValidator.validate(
                2, guide(GuideType.UNKNOWN, "이름", "주소", 37.5, 127.0, null, null));

        assertThat(result.isValid()).isFalse();
        assertThat(result.excluded().reason()).isEqualTo("UNKNOWN_TYPE");
        assertThat(result.excluded().index()).isEqualTo(2);
    }

    private static Stream<Arguments> invalidCandidates() {
        return Stream.of(
                Arguments.of(0, "NAME_MISSING", guide(GuideType.PLACE, null, "주소", 37.5, 127.0, null, null)),
                Arguments.of(0, "NAME_MISSING", guide(GuideType.PLACE, " ", "주소", 37.5, 127.0, null, null)),
                Arguments.of(
                        0, "NAME_TOO_LONG", guide(GuideType.PLACE, "가".repeat(256), "주소", 37.5, 127.0, null, null)),
                Arguments.of(0, "ADDRESS_MISSING", guide(GuideType.PLACE, "이름", null, 37.5, 127.0, null, null)),
                Arguments.of(0, "ADDRESS_MISSING", guide(GuideType.PLACE, "이름", " ", 37.5, 127.0, null, null)),
                Arguments.of(
                        0, "ADDRESS_TOO_LONG", guide(GuideType.PLACE, "이름", "나".repeat(256), 37.5, 127.0, null, null)),
                Arguments.of(0, "TEXT_TOO_LONG", withText(GuideType.PLACE, "카".repeat(256), null, null)),
                Arguments.of(0, "TEXT_TOO_LONG", withText(GuideType.PLACE, null, "다".repeat(256), null)),
                Arguments.of(0, "TEXT_TOO_LONG", withText(GuideType.PLACE, null, null, "라".repeat(256))),
                Arguments.of(0, "INVALID_COORDINATE", guide(GuideType.PLACE, "이름", "주소", null, 127.0, null, null)),
                Arguments.of(0, "INVALID_COORDINATE", guide(GuideType.PLACE, "이름", "주소", 37.5, null, null, null)),
                Arguments.of(
                        0, "INVALID_COORDINATE", guide(GuideType.PLACE, "이름", "주소", Double.NaN, 127.0, null, null)),
                Arguments.of(
                        0,
                        "INVALID_COORDINATE",
                        guide(GuideType.PLACE, "이름", "주소", Double.POSITIVE_INFINITY, 127.0, null, null)),
                Arguments.of(0, "INVALID_COORDINATE", guide(GuideType.PLACE, "이름", "주소", 90.1, 127.0, null, null)),
                Arguments.of(0, "INVALID_COORDINATE", guide(GuideType.PLACE, "이름", "주소", 37.5, -180.1, null, null)),
                Arguments.of(
                        0,
                        "EVENT_PERIOD_INVALID",
                        guide(
                                GuideType.EVENT,
                                "행사",
                                "서울특별시 종로구 행사로 1",
                                37.5,
                                127.0,
                                LocalDate.of(2026, 10, 2),
                                LocalDate.of(2026, 10, 1))));
    }

    private static VideoAnalysisResponse.Guide withText(
            GuideType type, String category, String description, String locationDetail) {
        return new VideoAnalysisResponse.Guide(
                type,
                "이름",
                "서울특별시 종로구 주소로 1",
                37.5,
                127.0,
                description,
                locationDetail,
                category,
                null,
                type == GuideType.EVENT ? LocalDate.of(2026, 10, 1) : null,
                type == GuideType.EVENT ? LocalDate.of(2026, 10, 31) : null,
                false,
                0.9);
    }

    private static VideoAnalysisResponse.Guide guide(
            GuideType type, String name, String address, Double lat, Double lng, LocalDate start, LocalDate end) {
        return new VideoAnalysisResponse.Guide(
                type, name, address, lat, lng, "요약", "상세", "카페", null, start, end, false, 0.9);
    }
}
