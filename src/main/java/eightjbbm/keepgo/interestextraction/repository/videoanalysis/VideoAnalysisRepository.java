package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {}
