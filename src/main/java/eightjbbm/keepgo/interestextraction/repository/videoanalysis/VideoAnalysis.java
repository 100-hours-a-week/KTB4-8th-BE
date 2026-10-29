package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class VideoAnalysis {
    @Id
    @GeneratedValue
    private Long id;

    private String videoId;

    @Enumerated(EnumType.STRING)
    private VideoAnalysisStatus status;

    private Instant createDatetime;

    public VideoAnalysis(String videoId) {
        this.videoId = videoId;
        this.status = VideoAnalysisStatus.IN_PROGRESS;
        this.createDatetime = Instant.now();
    }

    public void markSuccess() {
        if (this.status != VideoAnalysisStatus.IN_PROGRESS) {
            throw new IllegalStateException();
        }
        this.status = VideoAnalysisStatus.SUCCESS;
    }

    public void markNotFound() {
        if (this.status != VideoAnalysisStatus.IN_PROGRESS) {
            throw new IllegalStateException();
        }
        this.status = VideoAnalysisStatus.NOT_FOUND;
    }

    public void markFailed() {
        if (this.status != VideoAnalysisStatus.IN_PROGRESS) {
            throw new IllegalStateException();
        }
        this.status = VideoAnalysisStatus.FAILED;
    }
}
