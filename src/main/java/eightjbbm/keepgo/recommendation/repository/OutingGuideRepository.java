package eightjbbm.keepgo.recommendation.repository;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutingGuideRepository extends JpaRepository<OutingGuide, Long> {

    /// 코스 추천 후보용. 장소명이 있는 장소·행사를 최근 등록 순으로 50개 조회한다.
    List<OutingGuide> findTop50ByNameIsNotNullOrderByIdDesc();
}
