package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoAnalysisResultRepository extends JpaRepository<VideoAnalysisResult, VideoAnalysisResultId> {

    @Query("""
        select v.outingGuide
        from VideoAnalysisResult v
        where v.videoAnalysis.id in :videoAnalysisIds
    """)
    Set<OutingGuide> findOutingGuidesByVideoAnalysisIdIn(@Param("videoAnalysisIds") Set<Long> videoAnalysisIds);
}
