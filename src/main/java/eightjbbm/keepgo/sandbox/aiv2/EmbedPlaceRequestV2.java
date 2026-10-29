package eightjbbm.keepgo.sandbox.aiv2;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;

public record EmbedPlaceRequestV2(List<Place> places) {
    public record Place(String placeId, String placeName, String summary) {}

    public static EmbedPlaceRequestV2 from(List<OutingGuide> outingGuides) {
        return new EmbedPlaceRequestV2(outingGuides.stream()
                .map(outingGuide ->
                        new Place(outingGuide.getId().toString(), outingGuide.getName(), outingGuide.getDescription()))
                .toList());
    }
}
