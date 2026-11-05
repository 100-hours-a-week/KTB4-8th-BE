package eightjbbm.keepgo.interestextraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("좋아요 영상 읽기")
class MemberLikedVideoReaderTest {

    private final YoutubeApiServiceV2 youtubeApiService = mock(YoutubeApiServiceV2.class);
    private final MemberLikedVideoReader reader = new MemberLikedVideoReader(youtubeApiService);

    @Test
    @DisplayName("다음 페이지가 있으면 끝까지 이어서 읽는다")
    void readsAllPages() {
        when(youtubeApiService.getVideoLists(1L, null))
                .thenReturn(
                        new VideoListResponse().setItems(List.of(video("a"))).setNextPageToken("next"));
        when(youtubeApiService.getVideoLists(1L, "next"))
                .thenReturn(
                        new VideoListResponse().setItems(List.of(video("b"))).setNextPageToken(null));

        List<Video> videos = reader.readAll(1L);

        assertThat(videos).extracting(Video::getId).containsExactly("a", "b");
        verify(youtubeApiService).getVideoLists(1L, null);
        verify(youtubeApiService).getVideoLists(1L, "next");
    }

    private Video video(String id) {
        return new Video().setId(id);
    }
}
