package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.util.cache.slot.SlotValue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/// AI 서버가 주고받는 슬롯 형식 (AI app/schemas/extract.py 의 Slots)
///
/// BE 내부 슬롯({@link SlotValue})과 타입이 달라서, AI와 통신할 때는 반드시 이 형식으로 변환한다.
/// 형식이 어긋나면 AI는 422를 내고, BE는 AI 응답을 역직렬화하지 못한다.
///
/// @param origin 출발지 이름. BE는 출발지를 좌표로 관리하므로 AI에는 보내지 않는다
/// @param region 지역
/// @param datetime "yyyy-MM-dd" 또는 "yyyy-MM-dd HH:mm"
/// @param availableTime 외출 가능 시간(분) "180"·"360"·"540"
/// @param category AI가 허용하는 카테고리 목록. null은 미정, 빈 목록은 상관없음
public record AiSlot(
        String origin,
        String region,
        String datetime,
        String availableTime,
        List<String> category
) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<Integer> AVAILABLE_TIMES = Set.of(180, 360, 540);
    private static final Set<String> CATEGORIES = Set.of("카페", "팝업", "전시", "맛집", "명소", "기타");

    public static AiSlot from(SlotValue slot) {
        if (slot == null) {
            return new AiSlot(null, null, null, null, null);
        }
        return new AiSlot(
                null,
                slot.getRegion(),
                formatDatetime(slot.getDatetime()),
                formatAvailableTime(slot.getAvailableTime()),
                filterCategories(slot.getCategory())
        );
    }

    /// 시각이 00:00이면 "날짜만 정해진 상태"로 보고 날짜만 보낸다.
    public static String formatDatetime(LocalDateTime datetime) {
        if (datetime == null) {
            return null;
        }
        return datetime.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? datetime.format(DATE)
                : datetime.format(DATE_TIME);
    }

    /// AI가 준 날짜를 BE 형식으로 바꾼다. 날짜만 오면 00:00으로 둔다. 형식이 다르면 null.
    public static LocalDateTime parseDatetime(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            return null;
        }
        try {
            return datetime.length() <= 10
                    ? LocalDate.parse(datetime, DATE).atStartOfDay()
                    : LocalDateTime.parse(datetime, DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String formatAvailableTime(Integer availableTime) {
        return availableTime != null && AVAILABLE_TIMES.contains(availableTime)
                ? availableTime.toString()
                : null;
    }

    public static Integer parseAvailableTime(String availableTime) {
        try {
            return availableTime == null ? null : Integer.valueOf(availableTime);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /// AI가 모르는 카테고리(FE 전용 라벨 등)가 섞이면 요청 전체가 422가 되므로 걸러낸다.
    ///
    /// 빈 목록은 AI에서 "상관없음"이라는 뜻이다. 그래서 걸러낸 뒤 하나도 안 남으면 "미정"(null)으로 보낸다.
    public static List<String> filterCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return categories;
        }
        List<String> known = categories.stream().filter(CATEGORIES::contains).toList();
        return known.isEmpty() ? null : known;
    }
}
