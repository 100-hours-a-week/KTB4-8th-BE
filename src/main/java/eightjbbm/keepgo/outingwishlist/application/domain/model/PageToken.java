package eightjbbm.keepgo.outingwishlist.application.domain.model;

import java.time.Instant;

public record PageToken(
        Long id,
        Instant createDatetime
) {}
