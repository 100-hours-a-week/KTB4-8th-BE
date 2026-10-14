package eightjbbm.keepgo.sandbox;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutingGuideRepository extends JpaRepository<OutingGuide, Long> {
}
