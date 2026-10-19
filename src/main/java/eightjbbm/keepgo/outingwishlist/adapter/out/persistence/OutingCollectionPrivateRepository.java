package eightjbbm.keepgo.outingwishlist.adapter.out.persistence;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.ds.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface OutingCollectionPrivateRepository extends JpaRepository<OutingCollectionPrivate, Long>, QuerydslPredicateExecutor<OutingCollectionPrivate> {

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
        from OutingCollectionPrivate ocp
        join ocp.outingGuide og
        where ocp.member.id = :memberId
            and og in :outingGuides
    """)
    Set<Address> findWishlistAddresses(
            @Param("memberId") Long memberId,
            @Param("outingGuides") Collection<OutingGuide> outingGuides
    );

    @Query("""
        select ocp
        from OutingCollectionPrivate ocp
        join fetch ocp.outingGuide og
        where ocp.member.id = :memberId
           and ocp.id = :outingWishlistEntryId
    """)
    Optional<OutingCollectionPrivate> findByMemberIdAndTypeAndOutingWishlistEntryId(
            @Param("memberId") Long memberId,
            @Param("outingWishlistEntryId") Long outingWishlistEntryId);
}
