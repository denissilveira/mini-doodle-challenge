package com.doodle.mini.service.meeting.exception;

import java.util.Set;
import java.util.UUID;

public class MeetingParticipantsNotFoundException extends RuntimeException {
    public MeetingParticipantsNotFoundException(Set<UUID> participantIds) {
        super("Meeting participants not found: " + participantIds);
    }
}
