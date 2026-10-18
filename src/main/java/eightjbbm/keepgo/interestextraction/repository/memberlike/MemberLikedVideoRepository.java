package eightjbbm.keepgo.interestextraction.repository.memberlike;

import eightjbbm.keepgo.sandbox.ds.VideoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberLikedVideoRepository extends JpaRepository<MemberLikedVideo, Long> {

    List<String> findAllByMemberId(Long memberId);

    @Query("""
        select m.videoId
        from MemberLikedVideo m
        where m.member.id = :memberId
            and m.videoId not in :videoIdList
        """)
    List<String> findUnexistingVideoIds(
            @Param("memberId") Long memberId,
            @Param("videoIdList") List<String> videoIdList
    );
}
