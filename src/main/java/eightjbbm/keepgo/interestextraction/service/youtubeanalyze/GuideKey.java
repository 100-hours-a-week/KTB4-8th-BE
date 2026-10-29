package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import eightjbbm.keepgo.common.domain.Address;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;

public record GuideKey(String name, Address address) {
    static GuideKey from(OutingGuide guide) {
        return new GuideKey(guide.getName(), guide.getAddress());
    }
}
