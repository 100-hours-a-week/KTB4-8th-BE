package eightjbbm.keepgo.interestextraction.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public interface InterestExtractionService {
    void syncWishlistFromYoutube(Long memberId);

    SseEmitter syncWishlistFromYoutubeSSE(Long memberId);
}
