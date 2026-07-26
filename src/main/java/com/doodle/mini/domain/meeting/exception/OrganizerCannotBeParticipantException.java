package com.doodle.mini.domain.meeting.exception;

import java.util.UUID;

public class OrganizerCannotBeParticipantException extends RuntimeException {
    public OrganizerCannotBeParticipantException(UUID userId) {
        super("Meeting organizer cannot also be an invited participant: " + userId);
    }
}
