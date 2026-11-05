package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.service.YoutubeAuthorizedClientService;
import eightjbbm.keepgo.util.exception.YoutubeNotConnectedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/// 등록된 YouTube 인증 정보로 연동 여부를 확인한다. 영상 목록은 호출하지 않는다.
@Service
@RequiredArgsConstructor
public class YoutubeAuthorizedConnectionChecker implements YoutubeConnectionChecker {

    private final YoutubeAuthorizedClientService authorizedClientService;

    @Override
    public void requireConnected(Long memberId) {
        try {
            authorizedClientService.getAccessToken(memberId);
        } catch (ResponseStatusException error) {
            int status = error.getStatusCode().value();
            if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.CONFLICT.value()) {
                throw new YoutubeNotConnectedException();
            }
            throw error;
        }
    }
}
