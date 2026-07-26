package com.doodle.mini.infrastructure.persistence.slot;

import com.doodle.mini.domain.slot.SlotStatus;

public interface SlotStatusCount {
    SlotStatus getStatus();
    long getTotal();
}
