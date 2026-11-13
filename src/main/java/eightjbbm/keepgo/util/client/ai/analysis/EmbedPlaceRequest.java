package eightjbbm.keepgo.util.client.ai.analysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;

public record EmbedPlaceRequest(long placeId, String placeName, String summary) {

    public static EmbedPlaceRequest from(OutingGuide outingGuide) {
        return new EmbedPlaceRequest(outingGuide.getId(), outingGuide.getName(), outingGuide.getDescription());
    }
}
