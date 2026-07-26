package com.doodle.mini.domain.slot;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeRange {

    @NotNull
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    public TimeRange(Instant startAt, Instant endAt) {
        Objects.requireNonNull(startAt, "startAt is required");
        Objects.requireNonNull(endAt, "endAt is required");
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("startAt must be before endAt");
        }
        this.startAt = startAt;
        this.endAt = endAt;
    }

    @AssertTrue(message = "Start time must be before end time")
    public boolean isValidRange() {
        return startAt != null
                && endAt != null
                && startAt.isBefore(endAt);
    }

}