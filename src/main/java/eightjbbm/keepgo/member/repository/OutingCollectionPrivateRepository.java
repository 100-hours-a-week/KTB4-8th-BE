package eightjbbm.keepgo.member.repository;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutingCollectionPrivateRepository extends JpaRepository<OutingCollectionPrivate, Long> {
}
