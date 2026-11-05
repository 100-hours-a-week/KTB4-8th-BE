package eightjbbm.keepgo.interestextraction.controller;

import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class InterestExtractionControllerV3 {

    private final YoutubeSyncCoordinator youtubeSyncCoordinator;

    @PostMapping(value = "/api/v3/member/youtube-sync", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter syncWishlistFromYoutube(@AuthenticationPrincipal Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        return youtubeSyncCoordinator.connect(memberId);
    }
}
