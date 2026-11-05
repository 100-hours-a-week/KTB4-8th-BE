package eightjbbm.keepgo.v4.member;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.v4.extractor.InterestExtractor;
import eightjbbm.keepgo.v4.youtube.YoutubeServiceV4;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberServiceV4 {

    private final YoutubeServiceV4 youtubeServiceV4;
    private final InterestExtractor interestExtractor;
    private final OutingWishlistServiceV4 outingWishlistServiceV4;
    //private final MemberLikedVideoRepository memberLikedVideoRepository;

    // 위시리스트 갱신
    public void foo(Long memberId) throws InterruptedException {
        // 외부 호출
        List<Video> likedVideos = youtubeServiceV4.fetchLikedVideos(memberId);

        // 이력 기반 필터링
        List<String> filteredVideoIds = filter(likedVideos);

        var threads = filteredVideoIds.stream().map(
                filteredVideoId -> Thread.ofVirtual()
                        .unstarted(() -> run(memberId, filteredVideoId)))
                .toList();

        threads.forEach(Thread::start);

        for (Thread thread : threads) {
            thread.join();
        }
    }

    // 이력 기반 필터링
    private List<String> filter(List<Video> likedVideos) {
        return List.of();
    }

    private void run(Long memberId, String videoId) {
        // 외부 호출
        List<OutingGuide> queryResult = interestExtractor.query(videoId);
        if (queryResult.isEmpty()) return; //NOT_FOUND 또는 FAILED

        // 외부 호출
        for (OutingGuide guide: queryResult) {
            outingWishlistServiceV4.foo(memberId, guide, videoId);
        }
    }
}
