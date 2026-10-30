package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoAnalysis {
    @Id
    @GeneratedValue
    private Long id;

    private String videoId;

    private Long youtubeSyncId;

    @Enumerated(EnumType.STRING)
    private VideoAnalysisStatus status;

    private Instant createDatetime;

    private Instant completedAt;

    public VideoAnalysis(String videoId) {
        this(videoId, null);
    }

    public VideoAnalysis(String videoId, Long youtubeSyncId) {
        this.videoId = videoId;
        this.youtubeSyncId = youtubeSyncId;
        this.status = VideoAnalysisStatus.IN_PROGRESS;
        this.createDatetime = Instant.now();
    }

    public void markSuccess() {
        complete(VideoAnalysisStatus.SUCCESS);
    }

    public void markNotFound() {
        complete(VideoAnalysisStatus.NOT_FOUND);
    }

    public void markFailed() {
        complete(VideoAnalysisStatus.FAILED);
    }

    private void complete(VideoAnalysisStatus status) {
        if (this.status != VideoAnalysisStatus.IN_PROGRESS) {
            throw new IllegalStateException();
        }
        this.status = status;
        this.completedAt = Instant.now();
    }
}
