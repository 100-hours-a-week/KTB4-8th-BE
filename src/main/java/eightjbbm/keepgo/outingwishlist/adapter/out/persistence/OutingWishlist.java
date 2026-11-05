package eightjbbm.keepgo.outingwishlist.adapter.out.persistence;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntryType;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;
import java.util.Optional;
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
        select ocp
        from OutingWishlistEntry ocp
        join fetch ocp.outingGuide og
        where ocp.member.id = :memberId
           and ocp.id = :outingWishlistEntryId
           and ocp.type = :type
           and ocp.deleteDatetime is null
    """)
    Optional<OutingWishlistEntry> findByMemberIdAndTypeAndOutingWishlistEntryId(
            @Param("memberId") Long memberId,
            @Param("outingWishlistEntryId") Long outingWishlistEntryId,
            @Param("type") OutingWishlistEntryType type);

    @Query("""
        select ocp
        from OutingWishlistEntry ocp
        where ocp.member.id = :memberId
           and ocp.outingCourse.id = :outingCourseId
           and ocp.type = :type
           and ocp.deleteDatetime is null
        order by ocp.id
    """)
    List<OutingWishlistEntry> findActiveByMemberIdAndOutingCourseId(
            @Param("memberId") Long memberId,
            @Param("outingCourseId") Long outingCourseId,
            @Param("type") OutingWishlistEntryType type);

    /// 삭제한 항목도 포함해 회원이 한 번이라도 위시리스트에 담았던 가이드를 찾는다.
    @Query("""
        select distinct og
        from OutingWishlistEntry owe
        join owe.outingGuide og
        where owe.member.id = :memberId
            and og in :outingGuides
    """)
    List<OutingGuide> findPreviouslyWishlistedGuides(
            @Param("memberId") Long memberId, @Param("outingGuides") List<OutingGuide> outingGuides);
}
