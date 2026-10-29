package eightjbbm.keepgo.recommendation.controller;

import eightjbbm.keepgo.recommendation.service.RecommendationServiceV2;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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
public class RecommendationControllerV2 {

    private final RecommendationServiceV2 recommendationService;

    @PostMapping(value = "/api/v2/member/recommendation", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> recommendOutingCourse(@AuthenticationPrincipal Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .body(recommendationService.recommendOutingCourse(memberId));
    }

    @PostMapping("/api/v2/member/recommendation/cancellation")
    public ResponseEntity<Void> cancelOutingCourseRecommendation(@AuthenticationPrincipal Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        recommendationService.cancelOutingCourseRecommendation(memberId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
