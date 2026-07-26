package com.doodle.mini.service.meeting;

import com.doodle.mini.api.meeting.CreateMeetingRequest;
import com.doodle.mini.api.meeting.MeetingResponse;
import com.doodle.mini.api.meeting.UpdateMeetingRequest;
import com.doodle.mini.domain.meeting.Meeting;
import com.doodle.mini.domain.meeting.exception.OrganizerCannotBeParticipantException;
import com.doodle.mini.domain.user.User;
import com.doodle.mini.infrastructure.persistence.meeting.MeetingRepository;
import com.doodle.mini.infrastructure.persistence.slot.SlotRepository;
import com.doodle.mini.infrastructure.persistence.user.UserRepository;
import com.doodle.mini.service.meeting.exception.MeetingNotFoundException;
import com.doodle.mini.service.meeting.exception.MeetingParticipantsNotFoundException;
import com.doodle.mini.service.meeting.exception.SlotNotAvailableForMeetingException;
import com.doodle.mini.service.slot.exception.SlotNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;

    @Transactional
    public MeetingResponse create(UUID slotId, CreateMeetingRequest request) {
        log.debug("Creating meeting. slotId={}", slotId);

        var slot = slotRepository.findForUpdateById(slotId)
            .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (!slot.isFree()) {
            throw new SlotNotAvailableForMeetingException(slotId, slot.getStatus());
        }

        var organizerId = slot.getCalendar().getUser().getId();
        var participants = findParticipants(request.participantIds(), organizerId);

        var meeting = Meeting.schedule(slot, request.title(), request.description());
        meeting.replaceParticipants(participants);
        meetingRepository.saveAndFlush(meeting);

        log.info("Meeting created successfully. meetingId={}, slotId={}, participantCount={}",
            meeting.getId(), slotId, participants.size());

        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse findById(UUID meetingId) {
        log.debug("Finding meeting. meetingId={}", meetingId);
        var meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        return MeetingResponse.from(meeting);
    }

    @Transactional
    public MeetingResponse update(UUID meetingId, UpdateMeetingRequest request) {
        log.debug("Updating meeting. meetingId={}", meetingId);

        var meeting = meetingRepository.findForUpdateById(meetingId)
            .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        var organizerId = meeting.getSlot().getCalendar().getUser().getId();
        var participants = findParticipants(request.participantIds(), organizerId);

        meeting.updateDetails(request.title(), request.description());
        meeting.replaceParticipants(participants);
        meetingRepository.flush();

        log.debug("Meeting updated successfully. meetingId={}, participantCount={}",
            meetingId, participants.size());

        return MeetingResponse.from(meeting);
    }

    @Transactional
    public void cancel(UUID meetingId) {
        log.debug("Cancelling meeting. meetingId={}", meetingId);

        var meeting = meetingRepository.findForUpdateById(meetingId)
            .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        var slotId = meeting.getSlot().getId();
        var slot = slotRepository.findForUpdateById(slotId)
            .orElseThrow(() -> new SlotNotFoundException(slotId));

        meetingRepository.delete(meeting);
        slot.cancelBooking();
        meetingRepository.flush();

        log.debug("Meeting cancelled successfully. meetingId={}, slotId={}", meetingId, slotId);
    }

    private Set<User> findParticipants(Set<UUID> requestedParticipantIds, UUID organizerId) {
        if (requestedParticipantIds == null || requestedParticipantIds.isEmpty()) {
            return Set.of();
        }

        var participantIds = Set.copyOf(requestedParticipantIds);
        if (participantIds.contains(organizerId)) {
            throw new OrganizerCannotBeParticipantException(organizerId);
        }

        var participants = userRepository.findAllById(participantIds);
        var foundIds = participants.stream().map(User::getId).collect(Collectors.toSet());
        var missingIds = new HashSet<>(participantIds);
        missingIds.removeAll(foundIds);

        if (!missingIds.isEmpty()) {
            throw new MeetingParticipantsNotFoundException(missingIds);
        }

        return new HashSet<>(participants);
    }
}
