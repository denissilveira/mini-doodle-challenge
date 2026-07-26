package com.doodle.mini.infrastructure.persistence.meeting;

import java.time.Instant;

public interface MeetingTimeRangeView {

    Instant getStartAt();

    Instant getEndAt();
}
