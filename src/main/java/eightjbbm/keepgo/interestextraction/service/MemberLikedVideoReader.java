package eightjbbm.keepgo.interestextraction.service;

import com.google.api.services.youtube.model.Video;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 회원이 좋아요 표시한 유튜브 영상을 페이지 끝까지 읽는다.
///
@Service
@RequiredArgsConstructor
public class MemberLikedVideoReader {

    private final YoutubeApiServiceV2 youtubeApiService;

    public List<Video> readAll(Long memberId) {
        String pageToken = null;
        List<Video> videos = new ArrayList<>();
        do {
            var response = youtubeApiService.getVideoLists(memberId, pageToken);
            videos.addAll(response.getItems());
            pageToken = response.getNextPageToken();
        } while (pageToken != null);
        return videos;
    }
}
