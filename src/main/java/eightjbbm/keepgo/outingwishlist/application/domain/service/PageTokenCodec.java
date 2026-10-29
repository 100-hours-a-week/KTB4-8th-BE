package eightjbbm.keepgo.outingwishlist.application.domain.service;

import eightjbbm.keepgo.outingwishlist.application.domain.model.PageToken;
import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class PageTokenCodec {

    private static final int PAYLOAD_SIZE = Long.BYTES // id
            + Long.BYTES // epochSecond
            + Integer.BYTES; // nano

    public String encode(PageToken pageToken) {
        if (pageToken == null || pageToken.id() == null || pageToken.createDatetime() == null) {
            throw new IllegalArgumentException("ID와 생성 일자는 필수입니다.");
        }

        Instant createdAt = pageToken.createDatetime();

        ByteBuffer buffer = ByteBuffer.allocate(PAYLOAD_SIZE);
        buffer.putLong(pageToken.id());
        buffer.putLong(createdAt.getEpochSecond());
        buffer.putInt(createdAt.getNano());

        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    public Optional<PageToken> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);

            if (decoded.length != PAYLOAD_SIZE) {
                return Optional.empty();
            }

            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            long id = buffer.getLong();
            long epochSecond = buffer.getLong();
            int nano = buffer.getInt();

            if (id <= 0 || nano < 0 || nano > 999_999_999) {
                return Optional.empty();
            }

            return Optional.of(new PageToken(id, Instant.ofEpochSecond(epochSecond, nano)));
        } catch (IllegalArgumentException | DateTimeException exception) {
            return Optional.empty();
        }
    }
}
