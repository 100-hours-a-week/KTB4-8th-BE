package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.dto.RecommendCourseRequest.RecommendHistoryPlaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/// 코스 추천의 저장 장소 이력 단위 테스트
///
/// 저장 장소의 createdAt이 기록되지 않아 추천 요청이 NPE로 500을 내던 문제(2026-10-02)의 회귀 테스트
public class RecommendHistoryPlaceUnitTests {

    @Test
    @DisplayName("""
            저장할 때 생성 시각을 기록한다
            """)
    void prePersistSetsCreatedAt() {
        var saved = new OutingCollectionPrivate(null, place(1L));

        ReflectionTestUtils.invokeMethod(saved, "prePersist");

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("""
            생성 시각이 없는 기존 저장 장소도 오늘 날짜로 보낸다
            """)
    void legacyPlaceWithoutCreatedAt() {
        var legacy = new OutingCollectionPrivate(null, place(7L));

        RecommendHistoryPlaceId history = RecommendHistoryPlaceId.from(legacy);

        assertThat(history.placeId()).isEqualTo("7");
        assertThat(history.savedAt()).isEqualTo(LocalDate.now());
    }

    private static OutingPlace place(Long id) {
        var place = new OutingPlace("카페", "어라운드 성수", "조용한 카페");
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }
}
