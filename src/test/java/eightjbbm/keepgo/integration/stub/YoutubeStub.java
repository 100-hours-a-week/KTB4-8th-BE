package eightjbbm.keepgo.integration.stub;

import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoContentDetails;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoStatus;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/// YouTube 좋아요 영상 조회 어댑터 스텁.
///
/// 여러 페이지, 토큰 만료, 일반 조회 오류를 재현한다.
public final class YoutubeStub implements YoutubeVideoProvider {

    private final List<List<Video>> pages = new CopyOnWriteArrayList<>();
    private final List<String> requestedPageTokens = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean tokenExpired;
    private volatile RuntimeException failure;
    private volatile int expireAfterCalls = -1;
    private volatile int failAfterCalls = -1;
    private volatile Duration delay = Duration.ZERO;

    public YoutubeStub addPage(List<Video> videos) {
        pages.add(List.copyOf(videos));
        return this;
    }

    public void expireToken() {
        tokenExpired = true;
    }

    /// 처음 N번 호출은 성공하고 그 뒤부터 토큰이 만료된 것처럼 동작한다.
    public void expireTokenAfter(int successfulCalls) {
        this.expireAfterCalls = successfulCalls;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /// 처음 N번 호출은 성공하고 그 뒤부터 주어진 오류를 던진다.
    public void failAfter(int successfulCalls, RuntimeException failure) {
        this.failAfterCalls = successfulCalls;
        this.failure = failure;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public void reset() {
        pages.clear();
        requestedPageTokens.clear();
        calls.set(0);
        tokenExpired = false;
        failure = null;
        expireAfterCalls = -1;
        failAfterCalls = -1;
        delay = Duration.ZERO;
    }

    public int callCount() {
        return calls.get();
    }

    public List<String> requestedPageTokens() {
        return java.util.Collections.unmodifiableList(new ArrayList<>(requestedPageTokens));
    }

    @Override
    public VideoListResponse getVideoLists(Long memberId, String pageToken) {
        int call = calls.incrementAndGet();
        requestedPageTokens.add(pageToken);
        sleep();

        if (tokenExpired || (expireAfterCalls >= 0 && call > expireAfterCalls)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "유튜브 인증이 만료되었습니다. 다시 연동해 주세요.");
        }
        if (failure != null && (failAfterCalls < 0 || call > failAfterCalls)) {
            throw failure;
        }

        int page = pageToken == null ? 0 : Integer.parseInt(pageToken);
        if (page >= pages.size()) {
            throw new IllegalStateException("스텁에 정의되지 않은 페이지입니다: " + pageToken);
        }

        VideoListResponse response = new VideoListResponse();
        response.setItems(pages.get(page));
        if (page + 1 < pages.size()) {
            response.setNextPageToken(String.valueOf(page + 1));
        }
        return response;
    }

    private void sleep() {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /// 필터를 통과하는 1분짜리 공개 업로드 영상을 만든다.
    public static Video video(String id) {
        VideoStatus status = new VideoStatus();
        status.setUploadStatus("uploaded");
        status.setPrivacyStatus("public");

        VideoContentDetails contentDetails = new VideoContentDetails();
        contentDetails.setDuration("PT1M");

        Video video = new Video();
        video.setId(id);
        video.setStatus(status);
        video.setContentDetails(contentDetails);
        return video;
    }

    public static List<Video> videos(String... ids) {
        List<Video> videos = new ArrayList<>(ids.length);
        for (String id : ids) {
            videos.add(video(id));
        }
        return videos;
    }
}
