package eightjbbm.keepgo.member.repository;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutingCollectionPrivateRepository extends JpaRepository<OutingCollectionPrivate, Long> {

    /// 회원의 저장 장소를 최근 저장 순으로 조회한다.
    List<OutingCollectionPrivate> findAllByMemberIdOrderByIdDesc(Long memberId);
}
