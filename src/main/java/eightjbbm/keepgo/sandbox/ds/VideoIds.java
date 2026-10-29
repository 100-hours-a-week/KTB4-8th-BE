package eightjbbm.keepgo.sandbox.ds;

import com.google.api.services.youtube.model.Video;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public class VideoIds {
    private final List<VideoId> videoIds;

    public VideoIds(List<VideoId> videoIds) {
        this.videoIds = videoIds;
    }

    public boolean contains(String videoId) {
        List<String> videoIds = this.videoIds.stream().map(VideoId::videoId).toList();
        return videoIds.contains(videoId);
    }

    public Stream<VideoId> stream() {
        return videoIds.stream();
    }

    public static VideoIds fromVideos(List<Video> videos) {
        return new VideoIds(videos.stream().map(Video::getId).map(VideoId::new).toList());
    }

    public static VideoIds fromVideoIds(List<VideoId> videoIds) {
        return new VideoIds(videoIds);
    }
}
