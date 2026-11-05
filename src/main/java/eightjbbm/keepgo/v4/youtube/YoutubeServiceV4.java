package eightjbbm.keepgo.v4.youtube;

import com.google.api.services.youtube.model.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class YoutubeServiceV4 {

    public List<Video> fetchLikedVideos(Long memberId) {
        // 사용자의 ID로 유튜브 액세스 토큰 fetch

        // 유튜브 액세스 토큰으로 사용자의 좋아요한 영상 목록 fetch

        // 유효한 영상 filter

        // 반환
        return null;
    }
}
