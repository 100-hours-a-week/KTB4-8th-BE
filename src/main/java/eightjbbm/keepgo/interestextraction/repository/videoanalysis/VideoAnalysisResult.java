package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class VideoAnalysisResult {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne
    @JoinColumn(name = "video_analysis_id")
    private VideoAnalysis videoAnalysis;
    @ManyToOne
    @JoinColumn(name = "outing_guide_id")
    private OutingGuide outingGuide;
    private Instant createDatetime;

    public VideoAnalysisResult(VideoAnalysis videoAnalysis, OutingGuide outingGuide) {
        this.videoAnalysis = videoAnalysis;
        this.outingGuide = outingGuide;
        this.createDatetime = Instant.now();
    }
}
