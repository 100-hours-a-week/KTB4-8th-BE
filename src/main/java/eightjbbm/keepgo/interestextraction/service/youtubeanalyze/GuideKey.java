package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import eightjbbm.keepgo.common.domain.Address;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;

/// 나들이 가이드의 동일성 기준. 유형·이름·도로명주소를 원문 그대로 비교한다.
///
/// 기존 호출자는 유형이 없는 생성자를 계속 쓸 수 있다. 새 동기화 경로는 `from`으로
/// 유형을 포함한 키를 만든다.
public record GuideKey(String type, String name, Address address) {

    public static final String PLACE = "place";
    public static final String EVENT = "event";

    public GuideKey(String name, Address address) {
        this(null, name, address);
    }

    public static GuideKey from(OutingGuide guide) {
        String type = guide instanceof OutingEvent ? EVENT : PLACE;
        return new GuideKey(type, guide.getName(), guide.getAddress());
    }
}
