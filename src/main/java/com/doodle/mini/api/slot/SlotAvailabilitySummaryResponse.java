package com.doodle.mini.api.slot;

import java.time.Instant;
import java.util.UUID;

public record SlotAvailabilitySummaryResponse(
        UUID userId,
        Instant from,
        Instant to,
        long total,
        long free,
        long booked,
        long blocked) {
}
