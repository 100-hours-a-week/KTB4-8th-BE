package eightjbbm.keepgo.chat.repository;

import eightjbbm.keepgo.chat.entity.Chat;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findAllByMemberId(Long memberId);

    @Query("""
        select c
        from Chat c
        where c.member.id = :memberId
            and c.deletedAt is null
            and (:cursor is null or c.id < :cursor)
        order by c.id desc
        """)
    List<Chat> findVisibleChats(@Param("memberId") Long memberId, @Param("cursor") Long cursor, Pageable pageable);

    Slice<Chat> findByMemberIdAndIdLessThanOrderByCreatedAtDescIdDesc(Long memberId, Long id, Pageable pageable);
}
