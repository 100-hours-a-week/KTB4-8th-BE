package eightjbbm.keepgo.database;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Profile("migration")
public class MySqlMigrationPreflight implements Callback {
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*");
    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE || event == Event.BEFORE_BASELINE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        try (var statement = context.getConnection().createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT VERSION(), @@version_comment,
                           @@character_set_client, @@character_set_connection, @@character_set_results
                    """)) {
                result.next();
                String version = result.getString(1);
                String vendor = version + " " + result.getString(2);
                var parsed = VERSION.matcher(version);
                if (vendor.toLowerCase(Locale.ROOT).contains("mariadb") || !parsed.matches()
                        || Integer.parseInt(parsed.group(1)) < 8
                        || (Integer.parseInt(parsed.group(1)) == 8
                        && Integer.parseInt(parsed.group(2)) == 0
                        && Integer.parseInt(parsed.group(3)) < 17)) {
                    throw new FlywayException("MySQL 8.0.17 or newer is required; found " + version);
                }
                for (int column = 3; column <= 4; column++) {
                    if (!"utf8mb4".equalsIgnoreCase(result.getString(column))) {
                        throw new FlywayException("MySQL client and connection must use utf8mb4");
                    }
                }
                // Connector/J can leave results NULL (no server-side conversion).
                String resultsCharset = result.getString(5);
                if (resultsCharset != null && !"utf8mb4".equalsIgnoreCase(resultsCharset)) {
                    throw new FlywayException("MySQL results must use utf8mb4 or no character conversion");
                }
            }
            try (var result = statement.executeQuery("""
                    SELECT PAD_ATTRIBUTE FROM information_schema.COLLATIONS
                    WHERE COLLATION_NAME = 'utf8mb4_0900_bin'
                    """)) {
                if (!result.next() || !"NO PAD".equals(result.getString(1))) {
                    throw new FlywayException("MySQL must support utf8mb4_0900_bin with NO PAD");
                }
            }
        } catch (SQLException e) {
            throw new FlywayException("MySQL migration preflight failed", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "mysql-migration-preflight";
    }
}
