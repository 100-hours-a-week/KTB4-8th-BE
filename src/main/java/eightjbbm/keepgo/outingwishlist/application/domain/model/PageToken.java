package eightjbbm.keepgo.outingwishlist.application.domain.model;

import jakarta.annotation.Nullable;

import java.time.Instant;

public record PageToken(
        @Nullable Long id,
        @Nullable Instant createDatetime
) {}
