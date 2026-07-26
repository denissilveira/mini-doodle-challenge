package com.doodle.mini.service.meeting.exception;

import com.doodle.mini.domain.slot.SlotStatus;
import java.util.UUID;

public class SlotNotAvailableForMeetingException extends RuntimeException {
    public SlotNotAvailableForMeetingException(UUID slotId, SlotStatus status) {
        super("Slot " + slotId + " is not available for a meeting. Current status: " + status);
    }
}
