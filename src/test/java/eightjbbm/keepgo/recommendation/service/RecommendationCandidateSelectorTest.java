package eightjbbm.keepgo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.recommendation.RecommendationProperties;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("추천 후보 선택")
class RecommendationCandidateSelectorTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-11-01T00:00:00Z");

    private final OutingWishlist outingWishlist = mock(OutingWishlist.class);
    private final KakaoPlaceSearch kakaoPlaceSearch = mock(KakaoPlaceSearch.class);
    private final RecommendationCandidateSelector selector =
            new RecommendationCandidateSelector(outingWishlist, kakaoPlaceSearch, new RecommendationProperties(5));

    @Test
    @DisplayName("반경 안의 장소와 기간 안의 행사만 거리순으로 고른다")
    void selectsNearbyPlacesAndOngoingEvents() throws Exception {
        var ongoingEvent = event(2L, "진행 행사", new Coordinate(37.5001f, 127.0f), -3600, 3600);
        var nearPlace = place(3L, "가까운 장소", new Coordinate(37.5002f, 127.0f));
        var farPlace = place(4L, "먼 장소", new Coordinate(38.0f, 127.0f));
        var invalidPlace = place(5L, "좌표 없음", new Coordinate(null, null));
        var endedEvent = event(6L, "끝난 행사", new Coordinate(37.5003f, 127.0f), -7200, -3600);
        when(outingWishlist.findRecommendationCandidates(1L))
                .thenReturn(List.of(nearPlace, ongoingEvent, farPlace, invalidPlace, endedEvent));
        when(kakaoPlaceSearch.getCoordinates("서울")).thenReturn(new Coordinate(37.5f, 127.0f));

        var selected = selector.select(1L, criteria("서울"));

        assertThat(selected).extracting(OutingGuide::getId).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("기준 위치를 찾지 못하면 503 예외로 바꾼다")
    void translatesLocationLookupFailure() throws Exception {
        when(kakaoPlaceSearch.getCoordinates("없는곳")).thenThrow(new IOException("kakao unavailable"));

        assertThatThrownBy(() -> selector.select(1L, criteria("없는곳"))).isInstanceOf(ServiceUnavailableException.class);
    }

    private OutingCriteria criteria(String location) {
        return OutingCriteria.emptyOutingCriteriaWithMemberId(1L)
                .update(new Coordinate(37.5f, 127.0f), location, REQUESTED_AT, 180, Set.of("place"), null);
    }

    private OutingPlace place(Long id, String name, Coordinate coordinate) {
        var place = new OutingPlace("카페", name, "설명", "상세", "서울", coordinate, null);
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private OutingEvent event(
            Long id, String name, Coordinate coordinate, long startOffsetSeconds, long endOffsetSeconds) {
        var event = new OutingEvent(
                "행사",
                name,
                "설명",
                "상세",
                "서울",
                coordinate,
                null,
                REQUESTED_AT.plusSeconds(startOffsetSeconds),
                REQUESTED_AT.plusSeconds(endOffsetSeconds));
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
