package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record VideoAnalysisResultId(
        @Column(name = "video_analysis_id") Long videoAnalysisId,

        @Column(name = "outing_guide_id") Long outingGuideId) implements Serializable {}
