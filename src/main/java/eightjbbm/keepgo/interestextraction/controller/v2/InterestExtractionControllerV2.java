package eightjbbm.keepgo.interestextraction.controller.v2;

import eightjbbm.keepgo.interestextraction.service.GetConnectedServiceListCommand;
import eightjbbm.keepgo.interestextraction.service.GetYoutubeConnectionCommand;
import eightjbbm.keepgo.interestextraction.service.UnlinkYoutubeAccountCommand;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.InterestExtractionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterestExtractionControllerV2 {
    private final InterestExtractionService interestExtractionService;

    @PostMapping("/api/v2/member/youtube-sync")
    public ResponseEntity<Void> syncWishlistFromYoutube(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        interestExtractionService.syncWishlistFromYoutube(memberId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    /// 연결된 서비스 목록 조회 API
    /// 
    @GetMapping("/api/v2/member/external-connections")
    public ResponseEntity<GetConnectedServiceListResponse> getConnectedServiceList(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        var result = interestExtractionService.getConnectedServiceList(
                new GetConnectedServiceListCommand(memberId));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(InterestExtractionMapper.interestExtractionMapper.fromResult(result));
    }

    /// 유튜브 연동 API
    /// 
    @PostMapping("/api/v2/member/external-connections/youtube")
    public ResponseEntity<LinkYoutubeAccountResponse> linkYoutubeAccount(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        String authorizationUrl = interestExtractionService.linkYoutubeAccount(
                memberId,
                request,
                response
        );
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new LinkYoutubeAccountResponse(authorizationUrl));
    }

    /// 유튜브 연동 정보 조회 API
    /// 
    @GetMapping("/api/v2/member/external-connections/youtube")
    public ResponseEntity<GetYoutubeConnectionResponse> getYoutubeConnection(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        var result = interestExtractionService.getYoutubeConnection(
                new GetYoutubeConnectionCommand(memberId));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(InterestExtractionMapper.interestExtractionMapper.fromResult(result));
    }

    /// 유튜브 연동 해제 API
    ///
    @DeleteMapping("/api/v2/member/external-connections/youtube")
    public ResponseEntity<Void> unlinkYoutubeAccount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long memberId = Long.valueOf(jwt.getSubject());
        interestExtractionService.unlinkYoutubeAccount(
                new UnlinkYoutubeAccountCommand(memberId));
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
