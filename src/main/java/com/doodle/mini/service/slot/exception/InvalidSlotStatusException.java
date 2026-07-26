package com.doodle.mini.service.slot.exception;

public class InvalidSlotStatusException extends RuntimeException {

    public InvalidSlotStatusException() {
        super("BOOKED status can only be assigned by scheduling a meeting");
    }
}
