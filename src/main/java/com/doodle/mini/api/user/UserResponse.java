package com.doodle.mini.api.user;

import com.doodle.mini.domain.calendar.Calendar;
import com.doodle.mini.domain.user.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String name,
    String email,
    String timezone,
    Instant createdAt,
    Instant updatedAt
) {

    public static UserResponse from(User user, Calendar calendar) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            calendar.getTimezone(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
