package eightjbbm.keepgo.recommendation;

import java.util.List;

public record GetHotPlaceListResponse(List<Place> places) {
    public record Place(String name, String address) {}
}
