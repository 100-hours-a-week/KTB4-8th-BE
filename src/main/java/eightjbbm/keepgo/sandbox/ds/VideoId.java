package eightjbbm.keepgo.sandbox.ds;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record VideoId(@Column(name = "video_id") String videoId) {
    public String toLink() {
        return "https://youtube.com/watch/" + videoId();
    }

    public static VideoId fromUrl(String url) {
        return new VideoId(url.split("/")[url.length() - 1]);
    }

    public static VideoId fromVideoId(String videoId) {
        return new VideoId(videoId);
    }
}
