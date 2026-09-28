package eightjbbm.keepgo.recommendation.repository;

import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OutingPlaceRepository extends JpaRepository<OutingPlace, Long> {
    Optional<OutingPlace> findByName(String name);
}
