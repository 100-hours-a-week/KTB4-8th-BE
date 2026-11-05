package eightjbbm.keepgo.v4.member;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutingWishlistServiceV4 {

    @Transactional
    public void foo(Long memberId, OutingGuide guide, String videoId) {
        idempotentSave(memberId, guide);
        updateLikedVideos(memberId, videoId);
    }

    private void idempotentSave(Long memberId, OutingGuide guide) {

    }

    private void updateLikedVideos(Long memberId, String videoId) {

    }
}
