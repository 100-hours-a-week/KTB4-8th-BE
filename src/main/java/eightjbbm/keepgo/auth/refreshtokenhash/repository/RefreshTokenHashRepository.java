package eightjbbm.keepgo.auth.refreshtokenhash.repository;

import java.util.Optional;

public interface RefreshTokenHashRepository {

    void save(RefreshTokenHash token);

    Optional<RefreshTokenHash> findById(String hash);

    Optional<RefreshTokenHash> consumeActive(String hash);
}
