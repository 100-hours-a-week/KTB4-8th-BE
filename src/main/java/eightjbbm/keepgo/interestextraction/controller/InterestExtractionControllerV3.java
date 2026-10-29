package eightjbbm.keepgo.interestextraction.controller;

import eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3.InterestExtractionServiceV3;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class InterestExtractionControllerV3 {

    private final InterestExtractionServiceV3 interestExtractionService;

    @PostMapping(value = "/api/v3/member/youtube-sync", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter syncWishlistFromYoutube(@AuthenticationPrincipal Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        return null;
    }

    @PostMapping("/api/v3/member/youtube-sync-plain")
    public ResponseEntity<Void> syncWishlistFromYoutubePlain(@AuthenticationPrincipal Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        interestExtractionService.syncWishlistFromYoutubePlain(memberId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
