package com.doodle.mini.service.user.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
    }

    public UserNotFoundException(String email) {
        super("User not found: " + email);
    }
}
