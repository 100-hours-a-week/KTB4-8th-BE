package eightjbbm.keepgo.sandbox.videoanalysis;

import eightjbbm.keepgo.sandbox.ds.VideoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {

    @Query("""
        select v.videoId
        from VideoAnalysis v
        where v.videoId in :videoIds
    """)
    public List<String> findExistingVideoIds(
            @Param("videoIds") List<String> videoIds
    );

    @Query("""
        select v.id
        from VideoAnalysis v
        where v.member.id = :memberId
            and v.videoId in :videoIds
    """)
    public Set<Long> findIdsByVideoIds(
            @Param("memberId") Long memberId,
            @Param("videoIds") List<String> videoIds
    );

    @Query("""
        select v
        from VideoAnalysis v
        where v.videoId in :videoId
            and v.createDatetime = (
                select max(r2.createdAt)
                from VideoAnalysis v2
                where v2.id = v.id
            )
    """)
    public List<VideoAnalysis> findAllLatestByVideoId(
            @Param("videoIds") List<VideoId> videoIds
    );
}
