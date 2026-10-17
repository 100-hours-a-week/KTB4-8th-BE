package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import eightjbbm.keepgo.sandbox.ds.VideoId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class VideoAnalysis {
    @Id @GeneratedValue
    private Long id;
    @Embedded
    private VideoId videoId;
    @Enumerated(EnumType.STRING)
    private VideoAnalysisStatus status;
    private Instant createDatetime;

    public VideoAnalysis(String videoId, VideoAnalysisStatus status) {
        this.videoId = VideoId.fromVideoId(videoId);
        this.status = status;
        this.createDatetime = Instant.now();
    }

    public VideoAnalysis(String videoId, String status) {
        this.videoId = VideoId.fromVideoId(videoId);
        switch (status) {
            case "success" -> this.status = VideoAnalysisStatus.SUCCESS;
            case "not_found" -> this.status = VideoAnalysisStatus.NOT_FOUND;
            case "failed" -> this.status = VideoAnalysisStatus.FAILED;
            default -> throw new IllegalStateException("invalid status: " + status);
        }
        this.createDatetime = Instant.now();
    }
}
