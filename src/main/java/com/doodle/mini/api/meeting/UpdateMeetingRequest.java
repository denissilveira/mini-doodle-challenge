package com.doodle.mini.api.meeting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UpdateMeetingRequest(
    @NotBlank @Size(max = 200) String title,
    String description,
    @Size(max = 100) Set<@NotNull UUID> participantIds) {
}
