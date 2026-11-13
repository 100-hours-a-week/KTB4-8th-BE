package eightjbbm.keepgo.auth.authorizationrequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class RdbAuthorizationRequestStore implements AuthorizationRequestStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    @Override
    public void save(String id, String json, String browserHash, Instant expiresAt) {
        jdbc.update(
                "INSERT INTO oauth_authorization_request (request_id, request_json, browser_hash, expires_at) VALUES (?, ?, ?, ?)",
                id,
                json,
                browserHash,
                Timestamp.from(expiresAt));
    }

    @Override
    public String find(String id, String browserHash, Instant now) {
        List<String> rows = jdbc.query(
                "SELECT request_json FROM oauth_authorization_request WHERE request_id = ? AND browser_hash = ? AND expires_at > ?",
                (rs, row) -> rs.getString(1),
                id,
                browserHash,
                Timestamp.from(now));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public String consume(String id, String browserHash, Instant now) {
        return transactions.execute(status -> {
            List<String> rows = jdbc.query(
                    "SELECT request_json FROM oauth_authorization_request WHERE request_id = ? AND browser_hash = ? AND expires_at > ? FOR UPDATE",
                    (rs, row) -> rs.getString(1),
                    id,
                    browserHash,
                    Timestamp.from(now));
            if (rows.isEmpty()) {
                return null;
            }
            jdbc.update("DELETE FROM oauth_authorization_request WHERE request_id = ?", id);
            return rows.getFirst();
        });
    }

    @Scheduled(fixedDelayString = "${keepgo.security.oauth2.authorization-request.cleanup-interval:600000}")
    public void cleanup() {
        jdbc.update("DELETE FROM oauth_authorization_request WHERE expires_at <= ?", Timestamp.from(Instant.now()));
    }
}
