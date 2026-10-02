package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationCommand;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.service.RecommendationService;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.cache.query.QueryCacheService;
import eightjbbm.keepgo.util.cache.recommendationjob.RecommendationJobCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.client.ai.AiServerApiClient;
import eightjbbm.keepgo.util.dto.RecommendCourseRequest;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse.RecommendData;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse.RecommendData.RecommendCourse;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse.RecommendData.RecommendCourse.RecommendPlace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// 코스 추천 요청 단위 테스트
///
/// 슬롯을 읽지 못하고(null), 전 회원의 저장 장소를 보내며, AI 스키마와 다른 형식으로
/// 요청하던 문제(2026-10-02)의 회귀 테스트
public class RecommendCourseRequestUnitTests {

    private static final Long MEMBER_ID = 1L;
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    @DisplayName("""
            추천 요청을 AI 스키마 형식으로 보낸다
            """)
    void serializeAsAiSchema() {
        SlotValue slot = SlotValue.from(
                new Coordinate(37.5f, 127.0364f), "서울 강남구", LocalDateTime.of(2026, 10, 9, 13, 0), 360, List.of("카페", "산책")
        );

        JsonNode json = MAPPER.valueToTree(RecommendCourseRequest.from(null, List.of(saved(7L)), slot));

        assertThat(json.get("query").asString()).isEmpty();
        assertThat(json.get("available_time").asString()).isEqualTo("360");
        assertThat(json.get("datetime").asString()).isEqualTo("2026-10-09 13:00");
        assertThat(json.has("date_time")).isFalse();
        assertThat(json.get("category")).hasSize(1);
        assertThat(json.get("origin").get("lat").asDouble()).isEqualTo(37.5, org.assertj.core.data.Offset.offset(1e-4));
        JsonNode candidate = json.get("candidates").get(0);
        assertThat(candidate.get("place_id").asString()).isEqualTo("7");
        assertThat(candidate.get("summary").asString()).isEmpty();
        assertThat(candidate.get("business_hours").asString()).isEqualTo("정보 없음");
        assertThat(json.get("history_place_ids").get(0).get("saved_at").isString()).isTrue();
    }

    @Test
    @DisplayName("""
            슬롯이 없으면 AI를 부르지 않고 400을 돌려준다
            """)
    void noSlot() {
        var fixture = new Fixture();
        when(fixture.slotCacheService.getSlot(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.requestRecommendation(new RequestRecommendationCommand(MEMBER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("""
            회원의 저장 장소만, 같은 장소는 한 번만 후보로 보낸다
            """)
    void memberPlacesOnlyAndDistinct() {
        var fixture = new Fixture();
        when(fixture.slotCacheService.getSlot(MEMBER_ID))
                .thenReturn(Optional.of(SlotValue.from(null, null, null, null, null)));
        when(fixture.repository.findAllByMemberIdOrderByIdDesc(MEMBER_ID))
                .thenReturn(List.of(saved(3L), saved(3L), saved(5L)));
        when(fixture.aiServerApiClient.recommendCourse(any())).thenThrow(new IllegalStateException("stop"));

        assertThatThrownBy(() -> fixture.service.requestRecommendation(new RequestRecommendationCommand(MEMBER_ID)));

        var captor = ArgumentCaptor.forClass(RecommendCourseRequest.class);
        verify(fixture.aiServerApiClient).recommendCourse(captor.capture());
        assertThat(captor.getValue().candidates())
                .extracting(RecommendCourseRequest.RecommendCandidate::placeId)
                .containsExactly("3", "5");
    }

    @Test
    @DisplayName("""
            AI가 고른 장소를 후보로 보낸 장소(guide id)에서 찾는다
            """)
    void mapResponsePlacesByGuideId() {
        var fixture = new Fixture();
        var event = new OutingEvent("팝업", "성수 팝업", null, null, null);
        ReflectionTestUtils.setField(event, "id", 9L);
        when(fixture.slotCacheService.getSlot(MEMBER_ID))
                .thenReturn(Optional.of(SlotValue.from(null, "서울 성동구", null, null, null)));
        when(fixture.repository.findAllByMemberIdOrderByIdDesc(MEMBER_ID))
                .thenReturn(List.of(saved(5L), new OutingCollectionPrivate(null, event)));
        when(fixture.aiServerApiClient.recommendCourse(any())).thenReturn(new RecommendCourseResponse("ok", new RecommendData(List.of(
                new RecommendCourse("성수 코스", List.of(
                        new RecommendPlace("5", "장소5", 0, "10:00", 60, "조용함"),
                        new RecommendPlace("9", "성수 팝업", 0, "11:00", 40, "인기")
                ), 100)
        ))));

        RequestRecommendationResult result = fixture.service.requestRecommendation(new RequestRecommendationCommand(MEMBER_ID));

        assertThat(result.details().getFirst().items())
                .extracting(RequestRecommendationResult.RecommendationDetailItem::category)
                .containsExactly("카페", "팝업");
        assertThat(result.metadata().scheduledTime()).isNull();
    }

    private static OutingCollectionPrivate saved(Long guideId) {
        var place = new OutingPlace("카페", "장소" + guideId, null);
        ReflectionTestUtils.setField(place, "id", guideId);
        return new OutingCollectionPrivate(null, place);
    }

    private static class Fixture {
        final AiServerApiClient aiServerApiClient = mock(AiServerApiClient.class);
        final OutingCollectionPrivateRepository repository = mock(OutingCollectionPrivateRepository.class);
        final SlotCacheService slotCacheService = mock(SlotCacheService.class);
        final RecommendationService service = new RecommendationService(
                aiServerApiClient,
                repository,
                mock(QueryCacheService.class),
                slotCacheService,
                mock(RecommendationJobCacheService.class)
        );
    }
}
