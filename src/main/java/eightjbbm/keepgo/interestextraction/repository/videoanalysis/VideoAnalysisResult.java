package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoAnalysisResult {
    @EmbeddedId
    private VideoAnalysisResultId id;

    @MapsId("videoAnalysisId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_analysis_id", nullable = false)
    private VideoAnalysis videoAnalysis;

    @MapsId("outingGuideId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outing_guide_id", nullable = false)
    private OutingGuide outingGuide;

    public VideoAnalysisResult(VideoAnalysis videoAnalysis, OutingGuide outingGuide) {
        this.videoAnalysis = videoAnalysis;
        this.outingGuide = outingGuide;
        this.id = new VideoAnalysisResultId(
                videoAnalysis.getId(),
                outingGuide.getId()
        );
    }
}
