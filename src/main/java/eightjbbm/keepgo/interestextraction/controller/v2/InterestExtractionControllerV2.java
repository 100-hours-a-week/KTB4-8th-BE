package eightjbbm.keepgo.interestextraction.controller.v2;

import eightjbbm.keepgo.interestextraction.service.YoutubeSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterestExtractionControllerV2 {
    private final YoutubeSyncService youtubeSyncService;

    @PostMapping("/api/v2/member/youtube-sync")
    public ResponseEntity<Void> foo(@AuthenticationPrincipal Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        youtubeSyncService.getLikedVideos(memberId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
