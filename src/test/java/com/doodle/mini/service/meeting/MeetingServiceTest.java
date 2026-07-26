package com.doodle.mini.service.meeting;

import com.doodle.mini.api.meeting.CreateMeetingRequest;
import com.doodle.mini.api.meeting.UpdateMeetingRequest;
import com.doodle.mini.domain.calendar.Calendar;
import com.doodle.mini.domain.meeting.Meeting;
import com.doodle.mini.domain.meeting.exception.OrganizerCannotBeParticipantException;
import com.doodle.mini.domain.slot.Slot;
import com.doodle.mini.domain.slot.TimeRange;
import com.doodle.mini.domain.user.User;
import com.doodle.mini.infrastructure.persistence.meeting.MeetingRepository;
import com.doodle.mini.infrastructure.persistence.slot.SlotRepository;
import com.doodle.mini.infrastructure.persistence.user.UserRepository;
import com.doodle.mini.service.meeting.exception.MeetingNotFoundException;
import com.doodle.mini.service.meeting.exception.MeetingParticipantsNotFoundException;
import com.doodle.mini.service.meeting.exception.SlotNotAvailableForMeetingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private SlotRepository slotRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private MeetingService meetingService;

    @Test
    @DisplayName("on create meeting, with free slot and valid participants, returns booked meeting")
    void onCreateWithFreeSlotAndValidParticipantsReturnsBookedMeeting() {
        var slot = freeSlot();
        var participant = participant();
        var request = new CreateMeetingRequest("Architecture discussion", "Initial design review", Set.of(participant.getId()));

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));
        when(userRepository.findAllById(Set.of(participant.getId()))).thenReturn(List.of(participant));
        when(meetingRepository.saveAndFlush(any(Meeting.class))).thenAnswer(i -> i.getArgument(0));

        var response = meetingService.create(slot.getId(), request);
        assertThat(response.id()).isNotNull();
        assertThat(response.slotId()).isEqualTo(slot.getId());
        assertThat(response.participants()).hasSize(1);
        assertThat(slot.isBooked()).isTrue();
        verify(meetingRepository, times(1)).saveAndFlush(any(Meeting.class));
    }

    @Test
    @DisplayName("on create meeting, with blocked slot, throws SlotNotAvailableForMeetingException")
    void onCreateWithBlockedSlotThrowsSlotNotAvailableForMeetingException() {
        var slot = freeSlot();
        slot.block();
        var request = new CreateMeetingRequest("Meeting", null, Set.of());

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> meetingService.create(slot.getId(), request))
                .isInstanceOf(SlotNotAvailableForMeetingException.class);
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("on create meeting, with missing participants, throws MeetingParticipantsNotFoundException")
    void onCreateWithMissingParticipantsThrowsMeetingParticipantsNotFoundException() {
        var slot = freeSlot();
        var missingId = UUID.randomUUID();
        var request = new CreateMeetingRequest("Meeting", null, Set.of(missingId));

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));
        when(userRepository.findAllById(Set.of(missingId))).thenReturn(List.of());

        assertThatThrownBy(() -> meetingService.create(slot.getId(), request))
                .isInstanceOf(MeetingParticipantsNotFoundException.class);
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("on create meeting, with organizer as participant, throws OrganizerCannotBeParticipantException")
    void onCreateWithOrganizerAsParticipantThrowsOrganizerCannotBeParticipantException() {
        var slot = freeSlot();
        var organizerId = slot.getCalendar().getUser().getId();
        var request = new CreateMeetingRequest("Meeting", null, Set.of(organizerId));

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> meetingService.create(slot.getId(), request))
                .isInstanceOf(OrganizerCannotBeParticipantException.class);
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("on find meeting, with existing meeting, returns meeting")
    void onFindByIdWithExistingMeetingReturnsMeeting() {
        var meeting = meeting();

        when(meetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));

        var response = meetingService.findById(meeting.getId());
        assertThat(response.id()).isEqualTo(meeting.getId());
    }

    @Test
    @DisplayName("on find meeting, with unknown meeting, throws MeetingNotFoundException")
    void onFindByIdWithUnknownMeetingThrowsMeetingNotFoundException() {
        var id = UUID.randomUUID();
        when(meetingRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> meetingService.findById(id)).isInstanceOf(MeetingNotFoundException.class);
    }

    @Test
    @DisplayName("on update meeting, with valid data, returns updated meeting")
    void onUpdateWithValidDataReturnsUpdatedMeeting() {
        var meeting = meeting();
        var participant = participant();
        var request = new UpdateMeetingRequest("Updated meeting", "Updated description", Set.of(participant.getId()));

        when(meetingRepository.findForUpdateById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(userRepository.findAllById(Set.of(participant.getId()))).thenReturn(List.of(participant));
        var response = meetingService.update(meeting.getId(), request);

        assertThat(response.title()).isEqualTo("Updated meeting");
        assertThat(response.participants()).hasSize(1);
        verify(meetingRepository, times(1)).flush();
    }

    @Test
    @DisplayName("on cancel meeting, with existing meeting, deletes meeting and releases slot")
    void onCancelWithExistingMeetingDeletesMeetingAndReleasesSlot() {
        var meeting = meeting();
        var slot = meeting.getSlot();

        when(meetingRepository.findForUpdateById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        meetingService.cancel(meeting.getId());

        assertThat(slot.isFree()).isTrue();
        verify(meetingRepository, times(1)).delete(meeting);
        verify(meetingRepository, times(1)).flush();
    }

    private Meeting meeting() {
        return Meeting.schedule(freeSlot(), "Architecture discussion", null);
    }

    private Slot freeSlot() {
        var organizer = User.create("Denis", "denis@example.com");
        var calendar = Calendar.createFor(organizer, ZoneId.of("Europe/Madrid"));
        return Slot.createFree(calendar, new TimeRange(
                Instant.parse("2026-07-28T09:00:00Z"),
                Instant.parse("2026-07-28T10:00:00Z")
        ));
    }

    private User participant() {
        return User.create("Participant", "participant@example.com");
    }
}
