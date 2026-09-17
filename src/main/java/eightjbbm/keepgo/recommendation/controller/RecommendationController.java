package eightjbbm.keepgo.recommendation.controller;

import eightjbbm.keepgo.recommendation.dto.*;
import eightjbbm.keepgo.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public void getOutingPlaceInfo() {
        /*
        장소 상세 조회 API
        - 미구현
         */
    }

    @GetMapping
    public void getOutingEventInfo() {
        /*
        이벤트 상세 조회 API
        - 미구현
         */
    }

    @GetMapping
    public void getOutingCourseInfo() {
        /*
        코스 상세 조회 API
        - 없어도 될듯?
         */
    }

    @GetMapping
    public void getAdvertisementList() {
        /*
        광고 목록 조회 API
        - 더미 데이터
         */
    }

    @GetMapping
    public void getAdvertisementInfo() {
        /*
        광고 상세 조회 API
        - 미구현
         */
    }

    @GetMapping
    public void getHotPlacesList() {
        /*
        요즘 뜨는 곳 목록 조회 API
        - 더미 데이터
         */
    }

    @PostMapping
    public RequestRecommendationResponse requestRecommendation(@AuthenticationPrincipal Jwt jwt, RequestRecommendationRequest request) {
        RequestRecommendationCommand command = new RequestRecommendationCommand(
                Long.valueOf(jwt.getSubject()),
                request.availableTime(),
                request.category(),
                request.originLat(),
                request.originLng(),
                request.requestedTime()
        );

        return RequestRecommendationResponse.from(
                recommendationService.requestRecommendation(command)
        );
    }

    @GetMapping
    public GetRecommendationCourseInfoResponse getRecommendedCourseInfo(@AuthenticationPrincipal Jwt jwt, GetRecommendedCourseInfoRequest request) {
        GetRecommendationCourseInfoCommand command = new GetRecommendationCourseInfoCommand(
                Long.valueOf(jwt.getSubject()),
                request.recommendationId()
        );

        return GetRecommendationCourseInfoResponse.from(
                recommendationService.getRecommendationCourseInfo(command)
        );
    }

    @PostMapping
    public StopRecommendationResponse stopRecommendation(@AuthenticationPrincipal Jwt jwt, StopRecommendationRequest request) {
        StopRecommendationCommand command = new StopRecommendationCommand(
                Long.valueOf(jwt.getSubject()),
                request.recommendationId()
        );

        return StopRecommendationResponse.from(
                recommendationService.stopRecommendation(command)
        );
    }
}
