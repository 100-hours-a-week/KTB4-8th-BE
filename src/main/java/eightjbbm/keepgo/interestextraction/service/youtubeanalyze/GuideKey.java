package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import eightjbbm.keepgo.common.domain.Address;
import eightjbbm.keepgo.recommendation.entity.GuideType;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;

/// 나들이 가이드의 동일성 키는 (유형, 이름, 도로명주소)의 정확한 원문 조합이다.
///
/// trim·대소문자 변환·유니코드 정규화를 하지 않는다. DB의 바이너리 collation과 같은 결과를 내야 한다.
public record GuideKey(GuideType type, String name, Address address) {
    public static GuideKey from(OutingGuide guide) {
        return new GuideKey(guide.guideType(), guide.getName(), guide.getAddress());
    }
}
