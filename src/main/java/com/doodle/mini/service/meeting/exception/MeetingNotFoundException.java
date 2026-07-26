package com.doodle.mini.service.meeting.exception;

import java.util.UUID;

public class MeetingNotFoundException extends RuntimeException {
    public MeetingNotFoundException(UUID meetingId) {
        super("Meeting not found: " + meetingId);
    }
}
