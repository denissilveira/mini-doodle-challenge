package com.doodle.mini.infrastructure.persistence.meeting;

import com.doodle.mini.domain.meeting.Meeting;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Meeting> findForUpdateById(UUID meetingId);

    // countQuery prevents Spring Data from generating an invalid count query derived from the join fetch
    @Query(value = """
        select m from Meeting m
        join fetch m.slot s
        join fetch s.calendar c
        join fetch c.user
         where c.user.id = :userId
            or exists (
                select 1
                  from MeetingParticipant participant
                 where participant.meeting = m
                   and participant.user.id = :userId
            )
        """,
        countQuery = """
        select count(m) from Meeting m
         where m.slot.calendar.user.id = :userId
            or exists (
                select 1
                  from MeetingParticipant participant
                 where participant.meeting = m
                   and participant.user.id = :userId
            )
        """)
    Page<Meeting> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
        select slot.timeRange.startAt as startAt,
               slot.timeRange.endAt as endAt
          from Meeting meeting
          join meeting.slot slot
          join meeting.participants participant
         where participant.user.id = :userId
           and slot.timeRange.startAt < :to
           and slot.timeRange.endAt > :from
         order by slot.timeRange.startAt
        """)
    List<MeetingTimeRangeView> findParticipantMeetingTimeRanges(
        @Param("userId") UUID userId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
}
