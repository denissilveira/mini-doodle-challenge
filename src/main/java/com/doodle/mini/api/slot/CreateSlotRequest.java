package com.doodle.mini.api.slot;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateSlotRequest(
    @NotNull Instant startAt,
    @NotNull Instant endAt) {
}
