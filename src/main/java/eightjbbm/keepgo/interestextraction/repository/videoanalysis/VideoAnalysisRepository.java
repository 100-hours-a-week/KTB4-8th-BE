package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {

    /// 만료된 동기화에 속한 진행 중 분석만 실패로 마감한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VideoAnalysis a
               set a.status = eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus.FAILED
             where a.youtubeSyncId in :syncIds
               and a.status = eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus.IN_PROGRESS
            """)
    int markFailedBySyncIds(Collection<Long> syncIds);
}
