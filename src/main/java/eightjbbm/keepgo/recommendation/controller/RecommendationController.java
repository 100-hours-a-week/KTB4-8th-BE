package eightjbbm.keepgo.recommendation.controller;

import eightjbbm.keepgo.recommendation.RecommendationMapper;
import eightjbbm.keepgo.recommendation.dto.*;
import eightjbbm.keepgo.recommendation.service.RecommendationService;
import eightjbbm.keepgo.util.dto.stub.AdvertisementListResponse;
import eightjbbm.keepgo.util.dto.stub.TrendingPlacesResponse;
import eightjbbm.keepgo.util.dto.stub.UnimplementedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final MessageSource messageSource;

    /// 여정 코스 추천 요청 API
    /// @param jwt
    /// @return {@link RequestRecommendationResponse}
    @PostMapping("/user/recommendation")
    public ResponseEntity<RequestRecommendationResponse> requestRecommendation(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var command = RecommendationMapper.INSTANCE.toRequestRecommendationCommand(
                Long.valueOf(jwt.getSubject())
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        RequestRecommendationResponse.from(
                                recommendationService.requestRecommendation(command)
                        )
                );
    }


    /// 여정 코스 추천 중단 API
    /// @param jwt
    @PostMapping("/user/recommendation/cancellation")
    public ResponseEntity<Void> stopRecommendation(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var command = RecommendationMapper.INSTANCE.toStopRecommendationCommand(
                Long.valueOf(jwt.getSubject())
        );

        recommendationService.stopRecommendation(command);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    /// 장소 상세 조회 API
    ///
    /// (미구현)
    @GetMapping("/places/{placeId}")
    public ResponseEntity<UnimplementedResponse> getOutingPlaceInfo() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(UnimplementedResponse.from(messageSource.getMessage(
                        "unimplemented",
                        null,
                        LocaleContextHolder.getLocale()
                )));
    }

    /// 이벤트 상세 조회 API
    ///
    /// (미구현)
    @GetMapping("/events/{eventId}")
    public ResponseEntity<UnimplementedResponse> getOutingEventInfo() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(UnimplementedResponse.from(messageSource.getMessage(
                        "unimplemented",
                        null,
                        LocaleContextHolder.getLocale()
                )));
    }

    /// 광고 상세 조회 API
    ///
    /// (미구현)
    @GetMapping("/advertisements/{advertisementId}")
    public ResponseEntity<UnimplementedResponse> getAdvertisementInfo() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(UnimplementedResponse.from(messageSource.getMessage(
                        "unimplemented",
                        null,
                        LocaleContextHolder.getLocale()
                )));
    }

    /// 광고 목록 조회
    ///
    /// (더미 데이터 제공)
    @GetMapping("/advertisements")
    public ResponseEntity<AdvertisementListResponse> getAdvertisementList() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(AdvertisementListResponse.stub());
    }

    /// 요즘 뜨는 곳 목록 조회 API
    ///
    /// (더미 데이터 제공)
    @GetMapping("/places")
    public ResponseEntity<TrendingPlacesResponse> getHotPlacesList(
            @RequestParam String sort,
            @RequestParam Integer size,
            @RequestParam String cursor
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(TrendingPlacesResponse.stub());
    }
}
