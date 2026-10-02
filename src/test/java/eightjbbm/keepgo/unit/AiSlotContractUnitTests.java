package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.chat.dto.SendChatCommand;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.cache.getreply.GetReplyRequest;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.dto.AiSlot;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// BE ↔ AI /v1/extract 슬롯 계약 단위 테스트
///
/// AI 스키마(app/schemas/extract.py)와 형식이 어긋나 AI가 422를 내거나
/// BE가 AI 응답을 역직렬화하지 못하던 문제(2026-10-02)의 회귀 테스트
public class AiSlotContractUnitTests {

    /// application.yaml 의 spring.jackson.property-naming-strategy: SNAKE_CASE 와 같은 설정
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /// AI Slots.datetime 의 pattern
    private static final String AI_DATETIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2})?$";

    @Test
    @DisplayName("""
            AI 응답의 category 배열·시각 포함 datetime·출발지 문자열을 역직렬화한다
            """)
    void deserializeAiResponse() {
        String json = """
                {"message":"extract_success","data":{
                  "slot":{"origin":"강남역","region":"서울 강남구","datetime":"2026-10-03 14:00",
                          "available_time":"180","category":["카페","전시"]},
                  "query":"조용한 곳","bot_message":"좋아요"}}
                """;

        ExtractSlotResponse response = MAPPER.readValue(json, ExtractSlotResponse.class);

        AiSlot slot = response.data().slot();
        assertThat(slot.origin()).isEqualTo("강남역");
        assertThat(slot.category()).containsExactly("카페", "전시");
        assertThat(AiSlot.parseDatetime(slot.datetime())).isEqualTo(LocalDateTime.of(2026, 10, 3, 14, 0));
        assertThat(AiSlot.parseAvailableTime(slot.availableTime())).isEqualTo(180);
        assertThat(response.data().query()).isEqualTo("조용한 곳");
        assertThat(response.data().botMessage()).isEqualTo("좋아요");
    }

    @Test
    @DisplayName("""
            의도 카드로 채운 슬롯을 AI가 받는 prev_slot 형식으로 보낸다
            """)
    void serializeFilledPrevSlot() {
        SlotValue slot = SlotValue.from(
                new Coordinate(37.5f, 127.0364f),
                "서울 강남구 역삼동",
                LocalDateTime.of(2026, 10, 9, 13, 0, 0, 123),
                360,
                List.of("카페", "디저트")
        );

        JsonNode prevSlot = MAPPER.valueToTree(ExtractSlotRequest.from(request(slot))).get("prev_slot");

        assertThat(prevSlot.get("origin").isNull()).isTrue();
        assertThat(prevSlot.get("region").asString()).isEqualTo("서울 강남구 역삼동");
        assertThat(prevSlot.get("datetime").asString()).isEqualTo("2026-10-09 13:00").matches(AI_DATETIME_PATTERN);
        assertThat(prevSlot.get("available_time").isString()).isTrue();
        assertThat(prevSlot.get("available_time").asString()).isEqualTo("360");
        assertThat(prevSlot.get("category")).hasSize(1);
        assertThat(prevSlot.get("category").get(0).asString()).isEqualTo("카페");
    }

    @Test
    @DisplayName("""
            날짜만 정해진 슬롯은 날짜만 보낸다
            """)
    void serializeDateOnly() {
        SlotValue slot = SlotValue.from(null, null, LocalDate.of(2026, 10, 3).atStartOfDay(), null, null);

        JsonNode prevSlot = MAPPER.valueToTree(ExtractSlotRequest.from(request(slot))).get("prev_slot");

        assertThat(prevSlot.get("datetime").asString()).isEqualTo("2026-10-03").matches(AI_DATETIME_PATTERN);
    }

    @Test
    @DisplayName("""
            빈 슬롯과 AI가 모르는 값은 null로 보낸다
            """)
    void serializeEmptyAndUnknownValues() {
        SlotValue empty = SlotValue.from(null, null, null, null, null);
        SlotValue unknown = SlotValue.from(null, null, null, 200, List.of("산책"));

        JsonNode emptySlot = MAPPER.valueToTree(ExtractSlotRequest.from(request(empty))).get("prev_slot");
        JsonNode unknownSlot = MAPPER.valueToTree(ExtractSlotRequest.from(request(unknown))).get("prev_slot");

        assertThat(emptySlot.get("datetime").isNull()).isTrue();
        assertThat(emptySlot.get("available_time").isNull()).isTrue();
        assertThat(emptySlot.get("category").isNull()).isTrue();
        assertThat(unknownSlot.get("available_time").isNull()).isTrue();
        assertThat(unknownSlot.get("category").isNull()).isTrue();
    }

    @Test
    @DisplayName("""
            AI가 준 형식이 어긋난 값은 예외 없이 null로 읽는다
            """)
    void parseInvalidValues() {
        assertThat(AiSlot.parseDatetime("2026-10-03T14:00:00")).isNull();
        assertThat(AiSlot.parseDatetime("")).isNull();
        assertThat(AiSlot.parseAvailableTime("세시간")).isNull();
    }

    private static GetReplyRequest request(SlotValue slot) {
        return GetReplyRequest.from(new SendChatCommand(1L, "안녕"), LocalDate.of(2026, 10, 2), slot, null);
    }
}
