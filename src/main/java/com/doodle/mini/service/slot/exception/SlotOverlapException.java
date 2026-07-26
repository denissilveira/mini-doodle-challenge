package com.doodle.mini.service.slot.exception;

public class SlotOverlapException extends RuntimeException {

    public SlotOverlapException() {
        super("The requested time range overlaps an existing slot");
    }
}
