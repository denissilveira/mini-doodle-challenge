package com.doodle.mini.service.user.exception;

public class InvalidTimezoneException extends RuntimeException {

    public InvalidTimezoneException(String timezone) {
        super("Invalid timezone: " + timezone);
    }
}
