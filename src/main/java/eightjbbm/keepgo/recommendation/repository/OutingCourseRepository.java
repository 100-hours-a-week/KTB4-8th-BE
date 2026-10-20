package eightjbbm.keepgo.recommendation.repository;

import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutingCourseRepository extends JpaRepository<OutingCourse, Long> {
}
