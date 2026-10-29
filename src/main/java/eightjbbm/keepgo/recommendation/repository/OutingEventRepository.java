package eightjbbm.keepgo.recommendation.repository;

import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutingEventRepository extends JpaRepository<OutingEvent, Long> {
    Optional<OutingEvent> findByName(String name);
}
