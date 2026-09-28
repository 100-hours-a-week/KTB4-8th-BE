package eightjbbm.keepgo.chat.repository;

import eightjbbm.keepgo.chat.entity.Chat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findAllByMemberId(Long memberId);

    Slice<Chat> findByMemberIdAndIdLessThanOrderByCreatedAtDescIdDesc(Long memberId, Long id, Pageable pageable);
}
