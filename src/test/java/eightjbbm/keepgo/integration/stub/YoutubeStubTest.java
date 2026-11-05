package eightjbbm.keepgo.integration.stub;

import static eightjbbm.keepgo.integration.stub.YoutubeStub.videos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("YouTube 조회 어댑터 스텁")
class YoutubeStubTest {

    @Test
    @DisplayName("여러 페이지의 좋아요 영상을 순서대로 반환한다")
    void returnsMultiplePages() {
        YoutubeStub stub = new YoutubeStub().addPage(videos("video-a")).addPage(videos("video-b", "video-c"));

        VideoListResponse first = stub.getVideoLists(1L, null);
        VideoListResponse second = stub.getVideoLists(1L, first.getNextPageToken());

        assertThat(first.getItems()).extracting(Video::getId).containsExactly("video-a");
        assertThat(first.getNextPageToken()).isEqualTo("1");
        assertThat(second.getItems()).extracting(Video::getId).containsExactly("video-b", "video-c");
        assertThat(second.getNextPageToken()).isNull();
        assertThat(stub.requestedPageTokens()).containsExactly(null, "1");
    }

    @Test
    @DisplayName("토큰 만료를 재현한다")
    void reproducesExpiredToken() {
        YoutubeStub stub = new YoutubeStub().addPage(videos("video-a"));
        stub.expireToken();

        assertThatThrownBy(() -> stub.getVideoLists(1L, null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("일반 YouTube 조회 오류를 재현한다")
    void reproducesFetchFailure() {
        YoutubeStub stub = new YoutubeStub().addPage(videos("video-a"));
        stub.failWith(new ServiceUnavailableException());

        assertThatThrownBy(() -> stub.getVideoLists(1L, null)).isInstanceOf(ServiceUnavailableException.class);
    }
}
