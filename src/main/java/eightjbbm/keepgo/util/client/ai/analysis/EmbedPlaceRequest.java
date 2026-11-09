package eightjbbm.keepgo.util.client.ai.analysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;

public record EmbedPlaceRequest(List<Place> places) {
    public record Place(long placeId, String placeName, String summary) {}

    public static EmbedPlaceRequest from(List<OutingGuide> outingGuides) {
        return new EmbedPlaceRequest(outingGuides.stream()
                .map(outingGuide -> new Place(outingGuide.getId(), outingGuide.getName(), outingGuide.getDescription()))
                .toList());
    }
}
