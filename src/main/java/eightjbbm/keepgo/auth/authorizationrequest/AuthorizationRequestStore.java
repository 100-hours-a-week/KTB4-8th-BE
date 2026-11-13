package eightjbbm.keepgo.auth.authorizationrequest;

import java.time.Instant;

public interface AuthorizationRequestStore {
    void save(String id, String json, String browserHash, Instant expiresAt);

    String find(String id, String browserHash, Instant now);

    String consume(String id, String browserHash, Instant now);
}
