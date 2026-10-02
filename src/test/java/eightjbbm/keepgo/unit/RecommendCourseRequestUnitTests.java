package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationCommand;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// 코스 추천 요청 단위 테스트
///
/// 2026-10-02 회귀 테스트
/// - 슬롯을 읽지 못하고(null), AI 스키마와 다른 형식으로 요청하던 문제
/// - 이름 없는 장소가 후보에 섞여 AI가 422를 내던 문제
/// - 후보(candidates)와 취향 이력(history_place_ids)을 같은 목록으로 보내던 문제
/// - 임시 web 모드: 사용자 저장 데이터 없이 대화 조건만 보내기
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

        JsonNode json = MAPPER.valueToTree(RecommendCourseRequest.from(null, List.of(place(7L)), List.of(saved(3L)), slot));

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
        assertThat(json.get("history_place_ids").get(0).get("place_id").asString()).isEqualTo("3");
        assertThat(json.get("history_place_ids").get(0).get("saved_at").isString()).isTrue();
        assertThat(json.get("region").asString()).isEqualTo("서울 강남구");
    }

    @Test
    @DisplayName("""
            슬롯이 없으면 AI를 부르지 않고 400을 돌려준다
            """)
    void noSlot() {
        var fixture = new Fixture("saved");
        when(fixture.slotCacheService.getSlot(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.requestRecommendation(new RequestRecommendationCommand(MEMBER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("""
            후보는 전체 장소 풀에서, 이력은 회원의 저장 장소에서 중복 없이 보낸다
            """)
    void candidatesAndHistoryAreSeparate() {
        var fixture = new Fixture("saved");
        when(fixture.slotCacheService.getSlot(MEMBER_ID))
                .thenReturn(Optional.of(SlotValue.from(null, null, null, null, null)));
        when(fixture.guideRepository.findTop50ByNameIsNotNullOrderByIdDesc())
                .thenReturn(List.of(place(9L), place(5L)));
        when(fixture.collectionRepository.findAllByMemberIdOrderByIdDesc(MEMBER_ID))
                .thenReturn(List.of(saved(3L), saved(3L), saved(4L)));
        when(fixture.aiServerApiClient.recommendCourse(any())).thenThrow(new IllegalStateException("stop"));

        assertThatThrownBy(() -> fixture.service.requestRecommendation(new RequestRecommendationCommand(MEMBER_ID)));

        var captor = ArgumentCaptor.forClass(RecommendCourseRequest.class);
        verify(fixture.aiServerApiClient).recommendCourse(captor.capture());
        assertThat(captor.getValue().candidates())
                .extracting(RecommendCourseRequest.RecommendCandidate::placeId)
                .containsExactly("9", "5");
        assertThat(captor.getValue().historyPlaceIds())
                .extracting(RecommendCourseRequest.RecommendHistoryPlaceId::placeId)
                .containsExactly("3", "4");
    }

    @Test
    @DisplayName("""
            이름이 비어 있는 장소는 후보에서 뺀다
            """)
    void skipBlankNamedCandidates() {
        var fixture = new Fixture("saved");
        var blank = new OutingPlace("기타", " ", null);
        ReflectionTestUtils.setField(blank, "id", 353L);
        when(fixture.slotCacheService.getSlot(MEMBER_ID))
                .thenReturn(Optional.of(SlotValue.from(null, null, null, null, null)));
        when(fixture.guideRepository.findTop50ByNameIsNotNullOrderByIdDesc())
                .thenReturn(List.of(place(9L), blank, place(5L)));
        when(fixture.aiServerApiClient.recommendCourse(any())).thenThrow(new IllegalStateException("stop"));

        assertThatThrownBy(() -> fixture.service.requestRecommendation(new RequestRecommendationCommand(MEMBER_ID)));

        var captor = ArgumentCaptor.forClass(RecommendCourseRequest.class);
        verify(fixture.aiServerApiClient).recommendCourse(captor.capture());
        assertThat(captor.getValue().candidates())
                .extracting(RecommendCourseRequest.RecommendCandidate::placeId)
                .containsExactly("9", "5");
    }

    @Test
    @DisplayName("""
            web 모드(기본값)는 사용자 저장 데이터 없이 대화 조건만 보내고, 웹 장소도 응답으로 옮긴다
            """)
    void webModeSendsConditionsOnly() {
        var fixture = new Fixture(null);
        when(fixture.slotCacheService.getSlot(MEMBER_ID)).thenReturn(Optional.of(SlotValue.from(
                null, "서울 강남구", LocalDateTime.of(2026, 10, 3, 15, 0), 180, List.of("카페")
        )));
        when(fixture.aiServerApiClient.recommendCourse(any())).thenReturn(new RecommendCourseResponse("ok", new RecommendData(List.of(
                new RecommendCourse("강남 카페 코스", List.of(
                        new RecommendPlace("web-0-0", "웹 카페", 10, "15:10", 60, "조용함")
                ), 70)
        ))));

        RequestRecommendationResult result = fixture.service.requestRecommendation(new RequestRecommendationCommand(MEMBER_ID));

        var captor = ArgumentCaptor.forClass(RecommendCourseRequest.class);
        verify(fixture.aiServerApiClient).recommendCourse(captor.capture());
        assertThat(captor.getValue().candidates()).isEmpty();
        assertThat(captor.getValue().historyPlaceIds()).isEmpty();
        assertThat(captor.getValue().region()).isEqualTo("서울 강남구");
        assertThat(captor.getValue().datetime()).isEqualTo("2026-10-03 15:00");
        verifyNoInteractions(fixture.guideRepository, fixture.collectionRepository);
        var item = result.details().getFirst().items().getFirst();
        assertThat(item.name()).isEqualTo("웹 카페");
        assertThat(item.category()).isNull();
    }

    @Test
    @DisplayName("""
            AI가 고른 장소를 후보로 보낸 장소(guide id)에서 찾는다
            """)
    void mapResponsePlacesByGuideId() {
        var fixture = new Fixture("saved");
        var event = new OutingEvent("팝업", "성수 팝업", null, null, null);
        ReflectionTestUtils.setField(event, "id", 9L);
        when(fixture.slotCacheService.getSlot(MEMBER_ID))
                .thenReturn(Optional.of(SlotValue.from(null, "서울 성동구", null, null, null)));
        when(fixture.guideRepository.findTop50ByNameIsNotNullOrderByIdDesc())
                .thenReturn(List.of(place(5L), event));
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

    private static OutingGuide place(Long guideId) {
        var place = new OutingPlace("카페", "장소" + guideId, null);
        ReflectionTestUtils.setField(place, "id", guideId);
        return place;
    }

    private static OutingCollectionPrivate saved(Long guideId) {
        return new OutingCollectionPrivate(null, place(guideId));
    }

    /// @param candidateSource keepgo.recommendation.candidate-source 값. null이면 기본값(web)과 같다
    private static class Fixture {
        final AiServerApiClient aiServerApiClient = mock(AiServerApiClient.class);
        final OutingCollectionPrivateRepository collectionRepository = mock(OutingCollectionPrivateRepository.class);
        final OutingGuideRepository guideRepository = mock(OutingGuideRepository.class);
        final SlotCacheService slotCacheService = mock(SlotCacheService.class);
        final RecommendationService service = new RecommendationService(
                aiServerApiClient,
                collectionRepository,
                guideRepository,
                mock(QueryCacheService.class),
                slotCacheService,
                mock(RecommendationJobCacheService.class)
        );

        Fixture(String candidateSource) {
            ReflectionTestUtils.setField(service, "candidateSource", candidateSource);
        }
    }
}
