package eightjbbm.keepgo.recommendation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult.RecommendationDetail;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult.RecommendationDetailItem;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse.RecommendCourse;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse.RecommendPlace;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("코스 추천 상세 응답 변환")
class RequestRecommendationResultTest {

    @Test
    @DisplayName("순번은 1부터 시작하고 좌표와 장소 설명을 장소에서 가져옴")
    void mapsSequenceCoordinateAndDescription() {
        var first = outingPlace("카페", "카페 설명", new Coordinate(37.5f, 127.0f));
        var second = outingPlace("식당", "식당 설명", new Coordinate(37.6f, 127.1f));

        var detail = RecommendationDetail.from(
                course(recommendPlace("1", "카페", 10), recommendPlace("2", "식당", 20)),
                recommendPlace -> "1".equals(recommendPlace.placeId()) ? first : second);

        assertThat(detail.items())
                .extracting(
                        RecommendationDetailItem::sequence,
                        RecommendationDetailItem::description,
                        RecommendationDetailItem::lat,
                        RecommendationDetailItem::lng)
                .containsExactly(tuple(1, "카페 설명", 37.5f, 127.0f), tuple(2, "식당 설명", 37.6f, 127.1f));
    }

    @Test
    @DisplayName("좌표가 없는 장소는 lat과 lng를 null로 응답")
    void returnsNullCoordinateWhenPlaceHasNoCoordinate() {
        var withoutCoordinate = outingPlace("카페", "카페 설명", null);
        var withNullValues = outingPlace("식당", "식당 설명", new Coordinate(null, null));

        var detail = RecommendationDetail.from(
                course(recommendPlace("1", "카페", 10), recommendPlace("2", "식당", 20)),
                recommendPlace -> "1".equals(recommendPlace.placeId()) ? withoutCoordinate : withNullValues);

        assertThat(detail.items()).allSatisfy(item -> {
            assertThat(item.lat()).isNull();
            assertThat(item.lng()).isNull();
        });
    }

    private OutingPlace outingPlace(String name, String description, Coordinate coordinate) {
        return new OutingPlace("카페", name, description, "1층", "서울", coordinate, null);
    }

    private RecommendPlace recommendPlace(String placeId, String placeName, int travelMinutes) {
        return new RecommendPlace(placeId, placeName, travelMinutes, "10:00", 30, "이유");
    }

    private RecommendCourse course(RecommendPlace... places) {
        return new RecommendCourse("코스", List.of(places), 60);
    }
}
