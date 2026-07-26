package com.doodle.mini.service.slot.exception;

public class InvalidSlotTimeRangeException extends RuntimeException {

    public InvalidSlotTimeRangeException() {
        super("Start time must be before end time");
    }
}
