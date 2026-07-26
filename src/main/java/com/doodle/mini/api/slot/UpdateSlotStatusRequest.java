package com.doodle.mini.api.slot;

import com.doodle.mini.domain.slot.SlotStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSlotStatusRequest(
    @NotNull SlotStatus status) {
}
