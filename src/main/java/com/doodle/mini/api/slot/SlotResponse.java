package com.doodle.mini.api.slot;

import com.doodle.mini.domain.slot.Slot;
import com.doodle.mini.domain.slot.SlotStatus;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(
    UUID id,
    Instant startAt,
    Instant endAt,
    SlotStatus status,
    Instant createdAt,
    Instant updatedAt) {

    public static SlotResponse from(Slot slot) {
        return new SlotResponse(
            slot.getId(),
            slot.getTimeRange().getStartAt(),
            slot.getTimeRange().getEndAt(),
            slot.getStatus(),
            slot.getCreatedAt(),
            slot.getUpdatedAt()
        );
    }
}
