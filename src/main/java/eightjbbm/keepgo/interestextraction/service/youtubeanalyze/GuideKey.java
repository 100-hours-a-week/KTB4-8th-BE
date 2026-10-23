package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.ds.Address;

public record GuideKey(
        String name,
        Address address
) {
    static GuideKey from(OutingGuide guide) {
        return new GuideKey(
                guide.getName(),
                guide.getAddress()
        );
    }
}
