package eightjbbm.keepgo.recommendation.controller;

import eightjbbm.keepgo.recommendation.dto.*;
import eightjbbm.keepgo.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 여정 코스 추천 요청 API
     * @param jwt
     * @param request {@link RequestRecommendationRequest}
     * @return
     */
    @PostMapping("/user/recommendation")
    public ResponseEntity<RequestRecommendationResponse> requestRecommendation(@AuthenticationPrincipal Jwt jwt, RequestRecommendationRequest request) {
        RequestRecommendationCommand command = new RequestRecommendationCommand(

        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        RequestRecommendationResponse.from(
                                recommendationService.requestRecommendation(command)
                        )
                );
    }

    /**
     * 여정 코스 추천 중단 API
     * @param jwt
     * @return
     */
    @PostMapping("/user/recommendation/cancellation")
    public ResponseEntity<Void> stopRecommendation(@AuthenticationPrincipal Jwt jwt) {
        StopRecommendationCommand command = new StopRecommendationCommand(

        );

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @GetMapping("/places/{placeId}")
    public void getOutingPlaceInfo() {
        /*
        장소 상세 조회 API
        - 미구현
         */
    }

    @GetMapping("/events/{eventId}")
    public void getOutingEventInfo() {
        /*
        이벤트 상세 조회 API
        - 미구현
         */
    }

    @GetMapping("/advertisements")
    public void getAdvertisementList() {
        /*
        광고 목록 조회 API
        - 더미 데이터
         */
    }

    @GetMapping("/advertisements/{advertisementId}")
    public void getAdvertisementInfo() {
        /*
        광고 상세 조회 API
        - 미구현
         */
    }

    @GetMapping("/places")
    public void getHotPlacesList(@RequestParam String sort, @RequestParam Integer size, @RequestParam String cursor) {
        /*
        요즘 뜨는 곳 목록 조회 API
        - 더미 데이터
         */
    }
}
