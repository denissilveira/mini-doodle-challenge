package com.doodle.mini.infrastructure.persistence.meeting;

import com.doodle.mini.domain.meeting.Meeting;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Meeting> findForUpdateById(UUID meetingId);

    // countQuery is required to avoid Hibernate's in-memory pagination when join fetch is present
    @Query(value = """
        select m from Meeting m
        join fetch m.slot s
        join fetch s.calendar c
        join fetch c.user
         where c.user.id = :userId
        """,
        countQuery = """
        select count(m) from Meeting m
         where m.slot.calendar.user.id = :userId
        """)
    Page<Meeting> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);
}
