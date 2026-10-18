package eightjbbm.keepgo.outingwishlist.adapter.in.web;

import eightjbbm.keepgo.outingwishlist.adapter.in.web.dto.*;
import eightjbbm.keepgo.outingwishlist.application.domain.service.dto.GetEventDetailCommand;
import eightjbbm.keepgo.outingwishlist.application.domain.service.dto.GetEventListCommand;
import eightjbbm.keepgo.outingwishlist.application.domain.service.dto.GetPlaceDetailCommand;
import eightjbbm.keepgo.outingwishlist.application.domain.service.dto.GetPlaceListCommand;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OutingWishlistController {

    private final OutingWishlistService outingWishlistService;

    /// 여행 위시리스트에 추가한 장소 목록 조회 API
    ///
    @GetMapping("/api/v2/member/outing-wishlist/places")
    public ResponseEntity<GetPlaceListResponse> getPlaceList(
            @RequestParam(required = false) @NotBlank String location,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal @NotNull Jwt jwt
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        var result = outingWishlistService.getPlaceList(
                new GetPlaceListCommand(
                        memberId,
                        location,
                        cursor));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(OutingWishlistControllerMapper.INSTANCE.fromResult(result));
    }

    /// 여행 위시리스트에 추가한 장소 상세 조회 API
    ///
    @GetMapping("/api/v2/member/outing-wishlist/places/{placeId}")
    public ResponseEntity<GetPlaceDetailResponse> getPlaceDetail(
            @PathVariable @Positive Long placeId,
            @AuthenticationPrincipal @NotNull Jwt jwt
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        var result = outingWishlistService.getPlaceDetail(
                new GetPlaceDetailCommand(
                        memberId,
                        placeId));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(OutingWishlistControllerMapper.INSTANCE.fromResult(result));
    }

    /// 여행 위시리스트에 추가한 행사 목록 조회 API
    ///
    @GetMapping("/api/v2/member/outing-wishlist/events")
    public ResponseEntity<GetEventListResponse> getEventList(
            @RequestParam(required = false) @NotBlank boolean ongoing,
            @RequestParam(required = false) @NotBlank String cursor,
            @AuthenticationPrincipal @NotNull Jwt jwt
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        var result = outingWishlistService.getEventList(
                new GetEventListCommand(
                        memberId,
                        ongoing,
                        cursor));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(OutingWishlistControllerMapper.INSTANCE.fromResult(result));
    }

    /// 여행 위시리스트에 추가한 행사 상세 조회 API
    ///
    @GetMapping("/api/v2/member/outing-wishlist/events/{eventId}")
    public ResponseEntity<GetEventDetailResponse> getEventDetail(
            @PathVariable @Positive Long eventId,
            @AuthenticationPrincipal @NotNull Jwt jwt
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        var result = outingWishlistService.getEventDetail(
                new GetEventDetailCommand(
                        memberId,
                        eventId));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(OutingWishlistControllerMapper.INSTANCE.fromResult(result));
    }
}
