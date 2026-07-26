package com.doodle.mini.api.slot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SlotAvailabilityResponse(
        UUID userId,
        Instant from,
        Instant to,
        List<TimeInterval> free,
        List<TimeInterval> busy) {
}
