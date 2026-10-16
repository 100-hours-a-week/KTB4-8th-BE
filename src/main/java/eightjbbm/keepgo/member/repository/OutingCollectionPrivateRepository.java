package eightjbbm.keepgo.member.repository;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.ds.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface OutingCollectionPrivateRepository extends JpaRepository<OutingCollectionPrivate, Long> {

    @Query("""
        select o
        from OutingCollectionPrivate o
        where o.member.id = :memberId
            and o.outingGuide in :outingGuides
    """)
    List<OutingCollectionPrivate> findAllByMemberIdAndOutingGuides(
            @Param("memberId") Long memberId,
            @Param("outingGuides") Set<OutingGuide> outingGuides
    );

    @Query("""
        select distinct og.address
        from OutingcollectionPrivate ocp
        join ocp.outingGuide og
        where ocp.member.id = :memberId
            and og in :outingGuides
    """)
    Set<Address> findWishlistAddresses(
            @Param("memberId") Long memberId,
            @Param("outingGuides") Collection<OutingGuide> outingGuides
    );
}
