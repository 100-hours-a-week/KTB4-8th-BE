package eightjbbm.keepgo.sandbox.videoanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {

    @Query("""
        select v.videoId
        from VideoAnalysis v
        where v.member.id = :memberId
            and v.videoId in :videoIds
    """)
    public List<String> findExistingVideoIds(
            @Param("memberId") Long memberId,
            @Param("videoIds") List<String> videoIds
    );

    @Query("""
        select v.id
        from VideoAnalysis v
        where v.member.id = :memberId
            and v.videoId in :videoIds
    """)
    public List<Long> findIdsByVideoIds(
            @Param("memberId") Long memberId,
            @Param("videoIds") List<String> videoIds
    );
}
