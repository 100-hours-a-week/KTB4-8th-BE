package eightjbbm.keepgo.member.repository;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {
    Optional<OAuthAccount> findByIssuerAndSubject(String issuer, String subject);

    Optional<OAuthAccount> findByMember(Member member);
}
