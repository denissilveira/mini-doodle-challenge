package com.doodle.mini.infrastructure.persistence.slot;

import com.doodle.mini.domain.slot.Slot;
import com.doodle.mini.domain.slot.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Slot> findForUpdateById(UUID slotId);

    @Query("""
        select slot.calendar.id
          from Slot slot
         where slot.id = :slotId
        """)
    Optional<UUID> findCalendarIdById(@Param("slotId") UUID slotId);

    @Query("""
        select case when count(slot) > 0 then true else false end
          from Slot slot
         where slot.calendar.id = :calendarId
           and slot.timeRange.startAt < :endAt
           and slot.timeRange.endAt > :startAt
        """)
    boolean existsOverlappingSlot(
            @Param("calendarId") UUID calendarId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
        select case when count(slot) > 0 then true else false end
          from Slot slot
         where slot.calendar.id = :calendarId
           and slot.id <> :slotId
           and slot.timeRange.startAt < :endAt
           and slot.timeRange.endAt > :startAt
        """)
    boolean existsOverlappingSlotExcludingId(
        @Param("calendarId") UUID calendarId,
        @Param("slotId") UUID slotId,
        @Param("startAt") Instant startAt,
        @Param("endAt") Instant endAt
    );

    @Query("""
        select slot
          from Slot slot
         where slot.calendar.id = :calendarId
           and slot.timeRange.startAt < :to
           and slot.timeRange.endAt > :from
        """)
    Page<Slot> findAllByCalendarAndTimeRange(
        @Param("calendarId") UUID calendarId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );

    @Query("""
        select slot
          from Slot slot
         where slot.calendar.id = :calendarId
           and slot.timeRange.startAt < :to
           and slot.timeRange.endAt > :from
           and slot.status = :status
        """)
    Page<Slot> findAllByCalendarAndTimeRangeAndStatus(
        @Param("calendarId") UUID calendarId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("status") SlotStatus status,
        Pageable pageable
    );
}
