package eightjbbm.keepgo.recommendation.repository;

import eightjbbm.keepgo.recommendation.entity.OutingStop;
import eightjbbm.keepgo.recommendation.entity.OutingStopId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutingStopRepository extends JpaRepository<OutingStop, OutingStopId> {}
