package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.ExcludedCandidate;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.OutingGuideFactory;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;

/// AI가 준 후보 하나가 DB에 저장 가능한지 검증한다.
///
/// 이름·주소·좌표·행사 기간·문자열 길이처럼 저장을 거부해야 하는 조건을 한곳에 모은다.
/// 알 수 없는 유형은 이 검증에 들어오기 전에 영상 단위 실패로 처리한다.
public final class OutingGuideCandidateValidator {

    static final int MAX_TEXT_LENGTH = 255;

    static final String UNKNOWN_TYPE = "UNKNOWN_TYPE";
    static final String NAME_MISSING = "NAME_MISSING";
    static final String NAME_TOO_LONG = "NAME_TOO_LONG";
    static final String ADDRESS_MISSING = "ADDRESS_MISSING";
    static final String ADDRESS_TOO_LONG = "ADDRESS_TOO_LONG";
    static final String TEXT_TOO_LONG = "TEXT_TOO_LONG";
    static final String INVALID_COORDINATE = "INVALID_COORDINATE";
    static final String EVENT_PERIOD_INVALID = "EVENT_PERIOD_INVALID";
    static final String DB_CONSTRAINT_VIOLATION = "DB_CONSTRAINT_VIOLATION";

    private OutingGuideCandidateValidator() {}

    public static ValidationResult validate(int index, VideoAnalysisResponse.Guide guide) {
        if (guide.type() == null || guide.type() == VideoAnalysisResponse.GuideType.UNKNOWN) {
            return excluded(index, UNKNOWN_TYPE);
        }
        if (isBlank(guide.name())) {
            return excluded(index, NAME_MISSING);
        }
        if (guide.name().length() > MAX_TEXT_LENGTH) {
            return excluded(index, NAME_TOO_LONG);
        }
        if (isBlank(guide.address())) {
            return excluded(index, ADDRESS_MISSING);
        }
        if (guide.address().length() > MAX_TEXT_LENGTH) {
            return excluded(index, ADDRESS_TOO_LONG);
        }
        if (isTooLong(guide.category()) || isTooLong(guide.description()) || isTooLong(guide.locationDetail())) {
            return excluded(index, TEXT_TOO_LONG);
        }
        if (!isValidCoordinate(guide.lat(), guide.lng())) {
            return excluded(index, INVALID_COORDINATE);
        }
        if (guide.type() == VideoAnalysisResponse.GuideType.EVENT
                && guide.eventStartDate() != null
                && guide.eventEndDate() != null
                && guide.eventStartDate().isAfter(guide.eventEndDate())) {
            return excluded(index, EVENT_PERIOD_INVALID);
        }

        try {
            return new ValidationResult(OutingGuideFactory.createOutingGuide(guide), null);
        } catch (RuntimeException error) {
            // 길이·날짜처럼 미리 알 수 없는 DB 제약은 저장 시도 대신 후보 제외로 처리한다.
            return excluded(index, DB_CONSTRAINT_VIOLATION);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isTooLong(String value) {
        return value != null && value.length() > MAX_TEXT_LENGTH;
    }

    private static boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null
                && lng != null
                && Double.isFinite(lat)
                && Double.isFinite(lng)
                && lat >= -90
                && lat <= 90
                && lng >= -180
                && lng <= 180;
    }

    private static ValidationResult excluded(int index, String reason) {
        return new ValidationResult(null, new ExcludedCandidate(index, reason));
    }

    /// 후보가 저장 가능하면 guide가, 제외됐으면 excluded가 채워진다.
    public record ValidationResult(OutingGuide guide, ExcludedCandidate excluded) {
        public boolean isValid() {
            return guide != null;
        }
    }
}
