package com.doodle.mini.infrastructure.persistence.calendar;

import com.doodle.mini.domain.calendar.Calendar;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarRepository extends JpaRepository<Calendar, UUID> {

    Optional<Calendar> findByUserId(UUID userId);

    List<Calendar> findAllByUserIdIn(Collection<UUID> userIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Calendar> findOneByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Calendar> findForUpdateById(UUID calendarId);
}
