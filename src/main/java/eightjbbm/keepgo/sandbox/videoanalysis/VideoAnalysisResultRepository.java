package eightjbbm.keepgo.sandbox.videoanalysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface VideoAnalysisResultRepository extends JpaRepository<VideoAnalysisResult, Long> {

    @Query("""
        select v.outingGuide
        from VideoAnalysisResult v
        where v.videoAnalysis.id in :videoAnalysisIds
    """)
    Set<OutingGuide> findOutingGuidesByVideoAnalysisIdIn(
            @Param("videoAnalysisIds") Set<Long> videoAnalysisIds
    );
}
