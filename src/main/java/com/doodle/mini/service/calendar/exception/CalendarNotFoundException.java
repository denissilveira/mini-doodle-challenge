package com.doodle.mini.service.calendar.exception;

import java.util.UUID;

public class CalendarNotFoundException extends RuntimeException {

    public CalendarNotFoundException(UUID userId) {
        super("Calendar not found for user: " + userId);
    }
}
