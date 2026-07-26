package com.doodle.mini.service.slot.exception;

import java.util.UUID;

public class BookedSlotOperationException extends RuntimeException {

    public BookedSlotOperationException(UUID slotId) {
        super("Booked slot cannot be modified or deleted directly: " + slotId);
    }
}
