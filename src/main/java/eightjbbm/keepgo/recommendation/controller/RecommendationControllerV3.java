package eightjbbm.keepgo.recommendation.controller;

import eightjbbm.keepgo.recommendation.GetHotPlaceListResponse;
import eightjbbm.keepgo.recommendation.GetOngoingEventListResponse;
import eightjbbm.keepgo.recommendation.service.HotPlaceServiceV2;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecommendationControllerV3 {

    private final HotPlaceServiceV2 hotPlaceServiceV2;

    @GetMapping("/api/v3/places/hot")
    public ResponseEntity<GetHotPlaceListResponse> getHotPlaceList() {
        return ResponseEntity.status(HttpStatus.OK).body(hotPlaceServiceV2.getHotPlaceList());
    }

    @GetMapping("/api/v3/events/ongoing")
    public ResponseEntity<GetOngoingEventListResponse> getOngoingEventList() {
        return ResponseEntity.status(HttpStatus.OK).body(hotPlaceServiceV2.getOngoingEventList());
    }
}
