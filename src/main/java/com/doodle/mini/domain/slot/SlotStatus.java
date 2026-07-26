package com.doodle.mini.domain.slot;

public enum SlotStatus {
    FREE, // The slot is available.
    BLOCKED, // The user manually marked the interval as unavailable.
    BOOKED // A meeting is associated with the slot.
}
