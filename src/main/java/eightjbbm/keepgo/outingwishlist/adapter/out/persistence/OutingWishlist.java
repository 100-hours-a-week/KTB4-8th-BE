package eightjbbm.keepgo.outingwishlist.adapter.out.persistence;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntryType;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutingWishlist
        extends JpaRepository<OutingWishlistEntry, Long>, QuerydslPredicateExecutor<OutingWishlistEntry> {

    Optional<OutingWishlistEntry> findByMemberIdAndIdAndTypeAndDeleteDatetimeIsNull(
            Long memberId, Long wishlistEntryId, OutingWishlistEntryType type);

    @Query("""
    select distinct og
    from OutingWishlistEntry ocp
    join ocp.outingGuide og
    where ocp.member.id = :memberId
        and ocp.deleteDatetime is null
        and og.coordinate.lat is not null
        and og.coordinate.lng is not null
    """)
    List<OutingGuide> findRecommendationCandidates(@Param("memberId") Long memberId);

    @Query("""
        select o
        from OutingWishlistEntry o
        where o.member.id = :memberId
            and o.outingGuide in :outingGuides
            and o.deleteDatetime is null
    """)
    List<OutingWishlistEntry> findAllByMemberIdAndOutingGuides(
            @Param("memberId") Long memberId, @Param("outingGuides") Set<OutingGuide> outingGuides);

    @Query("""
        select ocp
        from OutingWishlistEntry ocp
        join fetch ocp.outingGuide og
        where ocp.member.id = :memberId
           and ocp.id = :outingWishlistEntryId
           and ocp.deleteDatetime is null
    """)
    Optional<OutingWishlistEntry> findByMemberIdAndTypeAndOutingWishlistEntryId(
            @Param("memberId") Long memberId, @Param("outingWishlistEntryId") Long outingWishlistEntryId);

    @Query("""
        select og
        from OutingWishlistEntry owe
        join owe.outingGuide og
        where owe.member.id = :memberId
            and og in :outingGuides
            and owe.deleteDatetime is null
    """)
    List<OutingGuide> findAllWishlistedOutingGuides(
            @Param("memberId") Long memberId, @Param("outingGuides") List<OutingGuide> outingGuides);
}
