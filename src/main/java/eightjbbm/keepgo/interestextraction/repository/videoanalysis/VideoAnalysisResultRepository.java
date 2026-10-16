package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoAnalysisResultRepository extends JpaRepository<VideoAnalysisResult, Long> {

    @Query("""
        select v.outingGuide
        from VideoAnalysisResult v
        where v.memberId = :memberId
            and v.videoAnalysis.id in :videoAnalysisIds
    """)
    public List<OutingGuide> findOutingGuidesByVideoAnalysisIds(
            @Param("memberId") Long memberId,
            @Param("videoAnalysisIds") List<Long> videoAnalysisIds
    );
}
