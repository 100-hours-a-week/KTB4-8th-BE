package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import com.google.api.services.youtube.model.Video;

import java.util.List;

public class YoutubeVideoValidator {
    public static List<Video> validate(List<Video> youtubeVideos) {
        return youtubeVideos.stream()
                .filter(video -> video.getStatus() != null)
                .filter(video -> "uploaded".equals(video.getStatus().getUploadStatus()))
                .filter(video -> "public".equals(video.getStatus().getPrivacyStatus()))
                .filter(video -> video.getContentDetails() != null)
                .filter(video -> video.getContentDetails().getDuration() != null)
                .filter(video -> java.time.Duration.parse(video.getContentDetails().getDuration())
                        .compareTo(java.time.Duration.ofMinutes(3)) <= 0)
                .toList();
    }
}
