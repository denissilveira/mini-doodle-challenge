package com.doodle.mini.service.slot;

import com.doodle.mini.api.slot.CreateSlotRequest;
import com.doodle.mini.api.slot.SlotAvailabilityResponse;
import com.doodle.mini.api.slot.SlotAvailabilitySummaryResponse;
import com.doodle.mini.api.slot.TimeInterval;
import com.doodle.mini.api.slot.UpdateSlotRequest;
import com.doodle.mini.api.slot.UpdateSlotStatusRequest;
import com.doodle.mini.domain.calendar.Calendar;
import com.doodle.mini.domain.slot.Slot;
import com.doodle.mini.domain.slot.SlotStatus;
import com.doodle.mini.domain.slot.TimeRange;
import com.doodle.mini.domain.user.User;
import com.doodle.mini.infrastructure.persistence.calendar.CalendarRepository;
import com.doodle.mini.infrastructure.persistence.meeting.MeetingRepository;
import com.doodle.mini.infrastructure.persistence.meeting.MeetingTimeRangeView;
import com.doodle.mini.infrastructure.persistence.slot.SlotRepository;
import com.doodle.mini.infrastructure.persistence.slot.SlotStatusCount;
import com.doodle.mini.domain.calendar.exception.CalendarNotFoundException;
import com.doodle.mini.service.slot.exception.BookedSlotOperationException;
import com.doodle.mini.service.slot.exception.InvalidSlotStatusException;
import com.doodle.mini.service.slot.exception.InvalidSlotTimeRangeException;
import com.doodle.mini.service.slot.exception.SlotNotFoundException;
import com.doodle.mini.service.slot.exception.SlotOverlapException;
import com.doodle.mini.shared.api.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    private static final Instant START_AT = Instant.parse("2026-08-15T09:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-08-15T10:00:00Z");

    @Mock private CalendarRepository calendarRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private SlotRepository slotRepository;
    @InjectMocks private SlotService slotService;

    @Test
    @DisplayName("on create slot, with valid data, returns created slot")
    void onCreateWithValidDataReturnsCreatedSlot() {
        var userId = UUID.randomUUID();
        var calendar = calendar(userId);
        var request = new CreateSlotRequest(START_AT, END_AT);

        when(calendarRepository.findOneByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.existsOverlappingSlot(calendar.getId(), START_AT, END_AT)).thenReturn(false);
        when(slotRepository.saveAndFlush(any(Slot.class))).thenAnswer(i -> i.getArgument(0));

        var response = slotService.create(userId, request);

        assertThat(response.startAt()).isEqualTo(START_AT);
        assertThat(response.endAt()).isEqualTo(END_AT);
        assertThat(response.status()).isEqualTo(SlotStatus.FREE);
        verify(slotRepository, times(1)).saveAndFlush(any(Slot.class));
    }

    @Test
    @DisplayName("on create slot, with calendar not found, throws CalendarNotFoundException")
    void onCreateWithCalendarNotFoundThrowsCalendarNotFoundException() {
        var userId = UUID.randomUUID();

        when(calendarRepository.findOneByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.create(userId, new CreateSlotRequest(START_AT, END_AT)))
                .isInstanceOf(CalendarNotFoundException.class);
        verify(slotRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("on create slot, with overlapping range, throws SlotOverlapException")
    void onCreateWithOverlappingRangeThrowsSlotOverlapException() {
        var userId = UUID.randomUUID();
        var calendar = calendar(userId);

        when(calendarRepository.findOneByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.existsOverlappingSlot(calendar.getId(), START_AT, END_AT)).thenReturn(true);

        assertThatThrownBy(() -> slotService.create(userId, new CreateSlotRequest(START_AT, END_AT)))
                .isInstanceOf(SlotOverlapException.class);
        verify(slotRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("on create slot, with end before start, throws InvalidSlotTimeRangeException")
    void onCreateWithEndBeforeStartThrowsInvalidSlotTimeRangeException() {
        var userId = UUID.randomUUID();
        var request = new CreateSlotRequest(END_AT, START_AT);

        assertThatThrownBy(() -> slotService.create(userId, request))
                .isInstanceOf(InvalidSlotTimeRangeException.class);
        verify(calendarRepository, never()).findOneByUserId(any());
    }

    @Test
    @DisplayName("on create slot, with equal start and end, throws InvalidSlotTimeRangeException")
    void onCreateWithEqualStartAndEndThrowsInvalidSlotTimeRangeException() {
        var userId = UUID.randomUUID();
        var request = new CreateSlotRequest(START_AT, START_AT);

        assertThatThrownBy(() -> slotService.create(userId, request))
                .isInstanceOf(InvalidSlotTimeRangeException.class);
    }

    @Test
    @DisplayName("on find slot by id, with existing slot, returns slot")
    void onFindByIdWithExistingSlotReturnsSlot() {
        var slot = freeSlot();

        when(slotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));

        var response = slotService.findById(slot.getId());

        assertThat(response.id()).isEqualTo(slot.getId());
        assertThat(response.status()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    @DisplayName("on find slot by id, with unknown slot, throws SlotNotFoundException")
    void onFindByIdWithUnknownSlotThrowsSlotNotFoundException() {
        var slotId = UUID.randomUUID();

        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.findById(slotId))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    @DisplayName("on list slots, with valid range, returns paged slots")
    void onFindAllWithValidRangeReturnsPagedSlots() {
        var userId = UUID.randomUUID();
        var calendar = calendar(userId);
        var slot = freeSlot(calendar);
        var pageable = PageRequest.of(0, 10);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.findAllByCalendarAndTimeRange(calendar.getId(), START_AT, END_AT, pageable))
                .thenReturn(new PageImpl<>(List.of(slot), pageable, 1));

        PageResponse<com.doodle.mini.api.slot.SlotResponse> response =
                slotService.findAll(userId, START_AT, END_AT, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("on list slots, with status filter, returns only matching slots")
    void onFindAllWithStatusFilterReturnsMatchingSlots() {
        var userId = UUID.randomUUID();
        var calendar = calendar(userId);
        var pageable = PageRequest.of(0, 10);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.findAllByCalendarAndTimeRangeAndStatus(
                calendar.getId(), START_AT, END_AT, SlotStatus.FREE, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var response = slotService.findAll(userId, START_AT, END_AT, SlotStatus.FREE, pageable);

        assertThat(response.content()).isEmpty();
        verify(slotRepository, times(1))
                .findAllByCalendarAndTimeRangeAndStatus(calendar.getId(), START_AT, END_AT, SlotStatus.FREE, pageable);
    }

    @Test
    @DisplayName("on list slots, with inverted range, throws InvalidSlotTimeRangeException")
    void onFindAllWithInvertedRangeThrowsInvalidSlotTimeRangeException() {
        var userId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> slotService.findAll(userId, END_AT, START_AT, null, pageable))
                .isInstanceOf(InvalidSlotTimeRangeException.class);
        verify(calendarRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("on update slot, with valid data, returns updated slot")
    void onUpdateWithValidDataReturnsUpdatedSlot() {
        var slot = freeSlot();
        var newStart = END_AT;
        var newEnd = END_AT.plusSeconds(3600);
        var request = new UpdateSlotRequest(newStart, newEnd);

        when(slotRepository.findCalendarIdById(slot.getId())).thenReturn(Optional.of(slot.getCalendar().getId()));
        when(calendarRepository.findForUpdateById(slot.getCalendar().getId())).thenReturn(Optional.of(slot.getCalendar()));
        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));
        when(slotRepository.existsOverlappingSlotExcludingId(
                slot.getCalendar().getId(), slot.getId(), newStart, newEnd)).thenReturn(false);

        var response = slotService.update(slot.getId(), request);

        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.endAt()).isEqualTo(newEnd);
        verify(slotRepository, times(1)).flush();
    }

    @Test
    @DisplayName("on update slot, with end before start, throws InvalidSlotTimeRangeException")
    void onUpdateWithEndBeforeStartThrowsInvalidSlotTimeRangeException() {
        var slotId = UUID.randomUUID();
        var request = new UpdateSlotRequest(END_AT, START_AT);

        assertThatThrownBy(() -> slotService.update(slotId, request))
                .isInstanceOf(InvalidSlotTimeRangeException.class);
        verify(slotRepository, never()).findCalendarIdById(any());
    }

    @Test
    @DisplayName("on update slot, with booked slot, throws BookedSlotOperationException")
    void onUpdateWithBookedSlotThrowsBookedSlotOperationException() {
        var slot = bookedSlot();
        var request = new UpdateSlotRequest(START_AT, END_AT);

        when(slotRepository.findCalendarIdById(slot.getId())).thenReturn(Optional.of(slot.getCalendar().getId()));
        when(calendarRepository.findForUpdateById(slot.getCalendar().getId())).thenReturn(Optional.of(slot.getCalendar()));
        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> slotService.update(slot.getId(), request))
                .isInstanceOf(BookedSlotOperationException.class);
        verify(slotRepository, never()).flush();
    }

    @Test
    @DisplayName("on update slot, with overlapping range, throws SlotOverlapException")
    void onUpdateWithOverlappingRangeThrowsSlotOverlapException() {
        var slot = freeSlot();
        var request = new UpdateSlotRequest(START_AT, END_AT);

        when(slotRepository.findCalendarIdById(slot.getId())).thenReturn(Optional.of(slot.getCalendar().getId()));
        when(calendarRepository.findForUpdateById(slot.getCalendar().getId())).thenReturn(Optional.of(slot.getCalendar()));
        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));
        when(slotRepository.existsOverlappingSlotExcludingId(
                slot.getCalendar().getId(), slot.getId(), START_AT, END_AT)).thenReturn(true);

        assertThatThrownBy(() -> slotService.update(slot.getId(), request))
                .isInstanceOf(SlotOverlapException.class);
    }

    @Test
    @DisplayName("on update slot, with unknown slot, throws SlotNotFoundException")
    void onUpdateWithUnknownSlotThrowsSlotNotFoundException() {
        var slotId = UUID.randomUUID();
        var request = new UpdateSlotRequest(START_AT, END_AT);

        when(slotRepository.findCalendarIdById(slotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.update(slotId, request))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    @DisplayName("on summarize slots, with valid range, returns aggregated counts")
    void onSummarizeWithValidRangeReturnsAggregatedCounts() {
        var userId = UUID.randomUUID();
        var calendar = calendar(userId);

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.countByStatusInRange(calendar.getId(), START_AT, END_AT))
                .thenReturn(List.of(
                        statusCount(SlotStatus.FREE, 3),
                        statusCount(SlotStatus.BOOKED, 1),
                        statusCount(SlotStatus.BLOCKED, 2)
                ));

        SlotAvailabilitySummaryResponse response = slotService.summarize(userId, START_AT, END_AT);

        assertThat(response.total()).isEqualTo(6);
        assertThat(response.free()).isEqualTo(3);
        assertThat(response.booked()).isEqualTo(1);
        assertThat(response.blocked()).isEqualTo(2);
    }

    @Test
    @DisplayName("on summarize slots, with inverted range, throws InvalidSlotTimeRangeException")
    void onSummarizeWithInvertedRangeThrowsInvalidSlotTimeRangeException() {
        var userId = UUID.randomUUID();

        assertThatThrownBy(() -> slotService.summarize(userId, END_AT, START_AT))
                .isInstanceOf(InvalidSlotTimeRangeException.class);
        verify(calendarRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("on summarize slots, with calendar not found, throws CalendarNotFoundException")
    void onSummarizeWithCalendarNotFoundThrowsCalendarNotFoundException() {
        var userId = UUID.randomUUID();

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.summarize(userId, START_AT, END_AT))
                .isInstanceOf(CalendarNotFoundException.class);
    }

    @Test
    @DisplayName("on get availability, with a participant meeting, subtracts it from free time")
    void onGetAvailabilitySubtractsParticipantMeetingFromFreeTime() {
        var userId = UUID.randomUUID();
        var calendar = calendar(userId);
        var windowFrom = Instant.parse("2026-08-15T08:00:00Z");
        var windowTo = Instant.parse("2026-08-15T13:00:00Z");
        var freeStart = Instant.parse("2026-08-15T09:00:00Z");
        var freeEnd = Instant.parse("2026-08-15T12:00:00Z");
        var meetingStart = Instant.parse("2026-08-15T10:00:00Z");
        var meetingEnd = Instant.parse("2026-08-15T11:00:00Z");
        var freeSlot = Slot.createFree(calendar, new TimeRange(freeStart, freeEnd));

        when(calendarRepository.findByUserId(userId)).thenReturn(Optional.of(calendar));
        when(slotRepository.findAllInRange(calendar.getId(), windowFrom, windowTo))
                .thenReturn(List.of(freeSlot));
        when(meetingRepository.findParticipantMeetingTimeRanges(userId, windowFrom, windowTo))
                .thenReturn(List.of(meetingRange(meetingStart, meetingEnd)));

        SlotAvailabilityResponse response = slotService.getAvailability(userId, windowFrom, windowTo);

        assertThat(response.free()).containsExactly(
                new TimeInterval(freeStart, meetingStart),
                new TimeInterval(meetingEnd, freeEnd)
        );
        assertThat(response.busy()).containsExactly(
                new TimeInterval(meetingStart, meetingEnd)
        );
    }

    @Test
    @DisplayName("on get availability, with inverted range, throws InvalidSlotTimeRangeException")
    void onGetAvailabilityWithInvertedRangeThrows() {
        var userId = UUID.randomUUID();

        assertThatThrownBy(() -> slotService.getAvailability(userId, END_AT, START_AT))
                .isInstanceOf(InvalidSlotTimeRangeException.class);
        verify(calendarRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("on update status to BLOCKED, with free slot, blocks the slot")
    void onUpdateStatusToBlockedWithFreeSlotBlocksTheSlot() {
        var slot = freeSlot();

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        var response = slotService.updateStatus(slot.getId(), new UpdateSlotStatusRequest(SlotStatus.BLOCKED));

        assertThat(response.status()).isEqualTo(SlotStatus.BLOCKED);
        verify(slotRepository, times(1)).flush();
    }

    @Test
    @DisplayName("on update status to FREE, with blocked slot, unblocks the slot")
    void onUpdateStatusToFreeWithBlockedSlotUnblocksTheSlot() {
        var slot = freeSlot();
        slot.block();

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        var response = slotService.updateStatus(slot.getId(), new UpdateSlotStatusRequest(SlotStatus.FREE));

        assertThat(response.status()).isEqualTo(SlotStatus.FREE);
        verify(slotRepository, times(1)).flush();
    }

    @Test
    @DisplayName("on update status to BOOKED, throws InvalidSlotStatusException")
    void onUpdateStatusToBookedThrowsInvalidSlotStatusException() {
        var slotId = UUID.randomUUID();

        assertThatThrownBy(() -> slotService.updateStatus(slotId, new UpdateSlotStatusRequest(SlotStatus.BOOKED)))
                .isInstanceOf(InvalidSlotStatusException.class);
        verify(slotRepository, never()).findForUpdateById(any());
    }

    @Test
    @DisplayName("on update status, with booked slot, throws BookedSlotOperationException")
    void onUpdateStatusWithBookedSlotThrowsBookedSlotOperationException() {
        var slot = bookedSlot();

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> slotService.updateStatus(slot.getId(), new UpdateSlotStatusRequest(SlotStatus.FREE)))
                .isInstanceOf(BookedSlotOperationException.class);
    }

    @Test
    @DisplayName("on delete slot, with free slot, deletes the slot")
    void onDeleteWithFreeSlotDeletesTheSlot() {
        var slot = freeSlot();

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        slotService.delete(slot.getId());

        verify(slotRepository, times(1)).delete(slot);
        verify(slotRepository, times(1)).flush();
    }

    @Test
    @DisplayName("on delete slot, with booked slot, throws BookedSlotOperationException")
    void onDeleteWithBookedSlotThrowsBookedSlotOperationException() {
        var slot = bookedSlot();

        when(slotRepository.findForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> slotService.delete(slot.getId()))
                .isInstanceOf(BookedSlotOperationException.class);
        verify(slotRepository, never()).delete(any());
    }

    @Test
    @DisplayName("on delete slot, with unknown slot, throws SlotNotFoundException")
    void onDeleteWithUnknownSlotThrowsSlotNotFoundException() {
        var slotId = UUID.randomUUID();

        when(slotRepository.findForUpdateById(slotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.delete(slotId))
                .isInstanceOf(SlotNotFoundException.class);
    }

    private static SlotStatusCount statusCount(SlotStatus status, long total) {
        return new SlotStatusCount() {
            public SlotStatus getStatus() { return status; }
            public long getTotal() { return total; }
        };
    }

    private static MeetingTimeRangeView meetingRange(Instant start, Instant end) {
        return new MeetingTimeRangeView() {
            public Instant getStartAt() { return start; }
            public Instant getEndAt() { return end; }
        };
    }

    private Calendar calendar(UUID userId) {
        var user = User.create("Denis", "denis@example.com");
        return Calendar.createFor(user, ZoneId.of("Europe/Madrid"));
    }

    private Calendar calendar() {
        return calendar(UUID.randomUUID());
    }

    private Slot freeSlot() {
        return Slot.createFree(calendar(), new TimeRange(START_AT, END_AT));
    }

    private Slot freeSlot(Calendar calendar) {
        return Slot.createFree(calendar, new TimeRange(START_AT, END_AT));
    }

    private Slot bookedSlot() {
        var slot = freeSlot();
        slot.book();
        return slot;
    }
}
