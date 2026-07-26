package com.doodle.mini.infrastructure.persistence.meeting;

import com.doodle.mini.domain.meeting.Meeting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Meeting> findForUpdateById(UUID meetingId);
}
