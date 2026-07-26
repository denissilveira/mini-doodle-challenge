package com.doodle.mini.api.meeting;

import com.doodle.mini.domain.meeting.Meeting;
import com.doodle.mini.domain.meeting.MeetingParticipant;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(
    UUID id,
    UUID slotId,
    UserSummaryResponse organizer,
    String title,
    String description,
    Instant startAt,
    Instant endAt,
    List<UserSummaryResponse> participants,
    Instant createdAt,
    Instant updatedAt) {

    public static MeetingResponse from(Meeting meeting) {
        var slot = meeting.getSlot();
        var participants = meeting.getParticipants().stream()
                .sorted(Comparator.comparing(participant -> participant.getUser().getName()))
                .map(MeetingParticipant::getUser)
                .map(UserSummaryResponse::from)
                .toList();
        return new MeetingResponse(
            meeting.getId(),
            slot.getId(),
            UserSummaryResponse.from(slot.getCalendar().getUser()),
            meeting.getTitle(),
            meeting.getDescription(),
            slot.getTimeRange().getStartAt(),
            slot.getTimeRange().getEndAt(),
            participants,
            meeting.getCreatedAt(),
            meeting.getUpdatedAt()
        );
    }
}
