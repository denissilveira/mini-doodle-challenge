package com.doodle.mini.domain.meeting;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingParticipantId implements Serializable {

    @NotNull
    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
