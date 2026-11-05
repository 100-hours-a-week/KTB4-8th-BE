package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("나들이 가이드 동일성 키")
class GuideKeyTest {

    private static final String ADDRESS = "서울특별시 종로구 스텁로 1";

    @Test
    @DisplayName("이름과 주소가 같아도 장소와 행사는 다른 키다")
    void distinguishesType() {
        GuideKey place = GuideKey.from(place("스텁 장소", ADDRESS));
        GuideKey event = GuideKey.from(event("스텁 장소", ADDRESS));

        assertThat(place).isNotEqualTo(event);
    }

    @Test
    @DisplayName("대소문자가 다르면 다른 키다")
    void distinguishesCase() {
        assertThat(GuideKey.from(place("Cafe", ADDRESS))).isNotEqualTo(GuideKey.from(place("cafe", ADDRESS)));
    }

    @Test
    @DisplayName("후행 공백이 다르면 다른 키다")
    void distinguishesTrailingWhitespace() {
        assertThat(GuideKey.from(place("Cafe ", ADDRESS))).isNotEqualTo(GuideKey.from(place("Cafe", ADDRESS)));
    }

    @Test
    @DisplayName("유니코드 조합 방식이 다르면 다른 키다")
    void distinguishesUnicodeComposition() {
        assertThat(GuideKey.from(place("caf\u00E9", ADDRESS)))
                .isNotEqualTo(GuideKey.from(place("cafe\u0301", ADDRESS)));
    }

    @Test
    @DisplayName("같은 유형·이름·주소는 같은 키다")
    void sameTripleIsSameKey() {
        assertThat(GuideKey.from(place("스텁 장소", ADDRESS))).isEqualTo(GuideKey.from(place("스텁 장소", ADDRESS)));
    }

    @Test
    @DisplayName("주소가 한쪽만 null이면 다른 키다")
    void distinguishesNullAddress() {
        assertThat(GuideKey.from(place("스텁 장소", null))).isNotEqualTo(GuideKey.from(place("스텁 장소", ADDRESS)));
    }

    @Test
    @DisplayName("기존 동일성 판단도 같은 유형·이름·주소 규칙을 쓴다")
    void isSameAsUsesTheSameRule() {
        OutingGuide place = place("스텁 장소", ADDRESS);
        OutingGuide event = event("스텁 장소", ADDRESS);

        assertThat(place.isSameAs(place("스텁 장소", ADDRESS))).isTrue();
        assertThat(place.isSameAs(event)).isFalse();
        assertThat(place.isSameAs(place("스텁 장소 ", ADDRESS))).isFalse();
        assertThat(place.isSameAs(place("스텁 장소", ADDRESS + " "))).isFalse();
    }

    private OutingGuide place(String name, String address) {
        return new OutingPlace("카페", name, "설명", "상세", address, new Coordinate(37.5f, 127.0f), null);
    }

    private OutingGuide event(String name, String address) {
        return new OutingEvent("전시", name, "설명", "상세", address, new Coordinate(37.5f, 127.0f), null, null, null);
    }
}
