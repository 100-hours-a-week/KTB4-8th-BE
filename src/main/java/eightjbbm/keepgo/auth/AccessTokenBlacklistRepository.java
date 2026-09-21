package eightjbbm.keepgo.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessTokenBlacklistRepository extends JpaRepository<AccessTokenBlacklist, Long> {
    Boolean existsByAccessToken(String accessToken);
}
