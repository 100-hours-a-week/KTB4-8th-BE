package eightjbbm.keepgo.recommendation.controller;

import eightjbbm.keepgo.util.dto.stub.AdvertisementListResponse;
import eightjbbm.keepgo.util.dto.stub.UnimplementedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private final MessageSource messageSource;

    /// 장소 상세 조회 API
    ///
    /// (미구현)
    @GetMapping("/places/{placeId}")
    public ResponseEntity<UnimplementedResponse> getOutingPlaceInfo() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(UnimplementedResponse.from(
                        messageSource.getMessage("unimplemented", null, LocaleContextHolder.getLocale())));
    }

    /// 이벤트 상세 조회 API
    ///
    /// (미구현)
    @GetMapping("/events/{eventId}")
    public ResponseEntity<UnimplementedResponse> getOutingEventInfo() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(UnimplementedResponse.from(
                        messageSource.getMessage("unimplemented", null, LocaleContextHolder.getLocale())));
    }

    /// 광고 상세 조회 API
    ///
    /// (미구현)
    @GetMapping("/advertisements/{advertisementId}")
    public ResponseEntity<UnimplementedResponse> getAdvertisementInfo() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(UnimplementedResponse.from(
                        messageSource.getMessage("unimplemented", null, LocaleContextHolder.getLocale())));
    }

    /// 광고 목록 조회
    ///
    /// (더미 데이터 제공)
    @GetMapping("/advertisements")
    public ResponseEntity<AdvertisementListResponse> getAdvertisementList() {
        return ResponseEntity.status(HttpStatus.OK).body(AdvertisementListResponse.stub());
    }
}
