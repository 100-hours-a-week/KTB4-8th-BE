package eightjbbm.keepgo.sandbox.videoanalysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class VideoAnalysisResult {
    private Long id;
    private VideoAnalysis videoAnalysis;
    private OutingGuide outingGuide;
    private Instant createDatetime;

    public VideoAnalysisResult(VideoAnalysis videoAnalysis, OutingGuide outingGuide) {
        this.videoAnalysis = videoAnalysis;
        this.outingGuide = outingGuide;
        this.createDatetime = Instant.now();
    }
}
