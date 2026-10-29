package eightjbbm.keepgo.recommendation.repository;

import eightjbbm.keepgo.common.domain.Address;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutingGuideRepository extends JpaRepository<OutingGuide, Long> {
    public Optional<OutingGuide> findByAddress(Address address);

    @Query("""
        select o
        from OutingGuide o
        where o.address in :addresses
    """)
    List<OutingGuide> findAllByAddresses(@Param("addresses") List<Address> addresses);
}
