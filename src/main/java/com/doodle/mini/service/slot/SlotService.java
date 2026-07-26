package com.doodle.mini.service.slot;

import com.doodle.mini.api.slot.CreateSlotRequest;
import com.doodle.mini.api.slot.SlotAvailabilityResponse;
import com.doodle.mini.api.slot.SlotAvailabilitySummaryResponse;
import com.doodle.mini.api.slot.SlotResponse;
import com.doodle.mini.api.slot.TimeInterval;
import com.doodle.mini.api.slot.UpdateSlotRequest;
import com.doodle.mini.api.slot.UpdateSlotStatusRequest;
import com.doodle.mini.domain.slot.Slot;
import com.doodle.mini.domain.slot.SlotStatus;
import com.doodle.mini.domain.slot.TimeRange;
import com.doodle.mini.infrastructure.persistence.calendar.CalendarRepository;
import com.doodle.mini.infrastructure.persistence.meeting.MeetingRepository;
import com.doodle.mini.infrastructure.persistence.slot.SlotRepository;
import com.doodle.mini.domain.calendar.exception.CalendarNotFoundException;
import com.doodle.mini.service.slot.exception.BookedSlotOperationException;
import com.doodle.mini.service.slot.exception.InvalidSlotStatusException;
import com.doodle.mini.service.slot.exception.InvalidSlotTimeRangeException;
import com.doodle.mini.service.slot.exception.SlotNotFoundException;
import com.doodle.mini.service.slot.exception.SlotOverlapException;
import com.doodle.mini.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

    private final CalendarRepository calendarRepository;
    private final SlotRepository slotRepository;
    private final MeetingRepository meetingRepository;

    @Transactional
    public SlotResponse create(UUID userId, CreateSlotRequest request) {
        validateTimeRange(request.startAt(), request.endAt());
        var timeRange = new TimeRange(request.startAt(), request.endAt());
        var calendar = calendarRepository.findOneByUserId(userId)
            .orElseThrow(() -> new CalendarNotFoundException(userId));

        if (slotRepository.existsOverlappingSlot(
            calendar.getId(),
            timeRange.getStartAt(),
            timeRange.getEndAt())) {
            throw new SlotOverlapException();
        }

        var slot = Slot.createFree(calendar, timeRange);
        slotRepository.saveAndFlush(slot);

        log.debug("Slot created. slotId={}, calendarId={}", slot.getId(), calendar.getId());
        return SlotResponse.from(slot);
    }

    @Transactional(readOnly = true)
    public SlotResponse findById(UUID slotId) {
        log.debug("Finding slot. slotId={}", slotId);
        return SlotResponse.from(findSlot(slotId));
    }

    @Transactional(readOnly = true)
    public PageResponse<SlotResponse> findAll(
        UUID userId,
        Instant from,
        Instant to,
        SlotStatus status,
        Pageable pageable) {

        validateTimeRange(from, to);
        var calendar = calendarRepository.findByUserId(userId)
            .orElseThrow(() -> new CalendarNotFoundException(userId));

        var slots = findSlots(calendar.getId(), from, to, status, pageable);
        var response = slots.map(SlotResponse::from);
        return PageResponse.from(response);
    }

    @Transactional(readOnly = true)
    public SlotAvailabilityResponse getAvailability(UUID userId, Instant from, Instant to) {
        validateTimeRange(from, to);
        var calendar = calendarRepository.findByUserId(userId)
                .orElseThrow(() -> new CalendarNotFoundException(userId));

        var slots = slotRepository.findAllInRange(calendar.getId(), from, to);
        var freeIntervals = slots.stream()
                .filter(Slot::isFree)
                .map(slot -> clip(
                        slot.getTimeRange().getStartAt(),
                        slot.getTimeRange().getEndAt(),
                        from,
                        to))
                .toList();

        var busyIntervals = new ArrayList<TimeInterval>();
        slots.stream()
                .filter(slot -> slot.isBooked() || slot.isBlocked())
                .map(slot -> clip(
                        slot.getTimeRange().getStartAt(),
                        slot.getTimeRange().getEndAt(),
                        from,
                        to))
                .forEach(busyIntervals::add);
        meetingRepository.findParticipantMeetingTimeRanges(userId, from, to).stream()
                .map(meeting -> clip(meeting.getStartAt(), meeting.getEndAt(), from, to))
                .forEach(busyIntervals::add);

        var busy = mergeIntervals(busyIntervals);
        var free = subtractIntervals(mergeIntervals(freeIntervals), busy);

        return new SlotAvailabilityResponse(userId, from, to, free, busy);
    }

    @Transactional(readOnly = true)
    public SlotAvailabilitySummaryResponse summarize(UUID userId, Instant from, Instant to) {
        validateTimeRange(from, to);
        var calendar = calendarRepository.findByUserId(userId)
                .orElseThrow(() -> new CalendarNotFoundException(userId));

        var rows = slotRepository.countByStatusInRange(calendar.getId(), from, to);
        long free = 0, booked = 0, blocked = 0;
        for (var row : rows) {
            switch (row.getStatus()) {
                case FREE    -> free    = row.getTotal();
                case BOOKED  -> booked  = row.getTotal();
                case BLOCKED -> blocked = row.getTotal();
            }
        }
        return new SlotAvailabilitySummaryResponse(userId, from, to, free + booked + blocked, free, booked, blocked);
    }

    @Transactional
    public SlotResponse update(UUID slotId, UpdateSlotRequest request) {
        validateTimeRange(request.startAt(), request.endAt());
        var timeRange = new TimeRange(request.startAt(), request.endAt());
        var calendarId = slotRepository.findCalendarIdById(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));
        calendarRepository.findForUpdateById(calendarId)
                .orElseThrow(() -> new CalendarNotFoundException(calendarId));

        Slot slot = findSlotForUpdate(slotId);

        if (slot.isBooked()) {
            throw new BookedSlotOperationException(slotId);
        }

        if (slotRepository.existsOverlappingSlotExcludingId(
            calendarId,
            slotId,
            timeRange.getStartAt(),
            timeRange.getEndAt()
        )) {
            throw new SlotOverlapException();
        }

        slot.moveTo(timeRange);
        slotRepository.flush();

        log.info("Slot updated. slotId={}", slotId);
        return SlotResponse.from(slot);
    }

    @Transactional
    public SlotResponse updateStatus(UUID slotId, UpdateSlotStatusRequest request) {
        if (request.status() == SlotStatus.BOOKED) {
            throw new InvalidSlotStatusException();
        }

        Slot slot = findSlotForUpdate(slotId);

        if (slot.isBooked()) {
            throw new BookedSlotOperationException(slotId);
        }

        if (request.status() == SlotStatus.BLOCKED) {
            slot.block();
        } else {
            slot.markAsFree();
        }

        slotRepository.flush();

        log.debug("Slot status updated. slotId={}, status={}", slotId, slot.getStatus());
        return SlotResponse.from(slot);
    }

    @Transactional
    public void delete(UUID slotId) {
        Slot slot = findSlotForUpdate(slotId);

        if (slot.isBooked()) {
            throw new BookedSlotOperationException(slotId);
        }

        slotRepository.delete(slot);
        slotRepository.flush();

        log.info("Slot deleted. slotId={}", slotId);
    }

    private static TimeInterval clip(Instant start, Instant end, Instant from, Instant to) {
        return new TimeInterval(
                start.isBefore(from) ? from : start,
                end.isAfter(to) ? to : end
        );
    }

    private static List<TimeInterval> mergeIntervals(List<TimeInterval> source) {
        var intervals = source.stream()
                .sorted(Comparator.comparing(TimeInterval::start))
                .toList();

        if (intervals.isEmpty()) {
            return List.of();
        }

        var merged = new ArrayList<TimeInterval>();
        var current = intervals.get(0);
        for (var next : intervals.subList(1, intervals.size())) {
            if (!current.end().isBefore(next.start())) {
                current = new TimeInterval(current.start(),
                        current.end().isAfter(next.end()) ? current.end() : next.end());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static List<TimeInterval> subtractIntervals(
            List<TimeInterval> freeIntervals,
            List<TimeInterval> busyIntervals) {

        var available = new ArrayList<TimeInterval>();
        int busyIndex = 0;

        for (var free : freeIntervals) {
            while (busyIndex < busyIntervals.size()
                    && !busyIntervals.get(busyIndex).end().isAfter(free.start())) {
                busyIndex++;
            }

            var cursor = free.start();
            int currentBusyIndex = busyIndex;
            while (currentBusyIndex < busyIntervals.size()) {
                var busy = busyIntervals.get(currentBusyIndex);
                if (!busy.start().isBefore(free.end())) {
                    break;
                }
                if (busy.start().isAfter(cursor)) {
                    available.add(new TimeInterval(cursor, busy.start()));
                }

                if (!busy.end().isBefore(free.end())) {
                    cursor = free.end();
                    break;
                }

                if (busy.end().isAfter(cursor)) {
                    cursor = busy.end();
                }
                currentBusyIndex++;
            }
            busyIndex = currentBusyIndex;

            if (cursor.isBefore(free.end())) {
                available.add(new TimeInterval(cursor, free.end()));
            }
        }
        return available;
    }

    private void validateTimeRange(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            throw new InvalidSlotTimeRangeException();
        }
    }

    private Slot findSlot(UUID slotId) {
        return slotRepository.findById(slotId)
            .orElseThrow(() -> new SlotNotFoundException(slotId));
    }

    private Slot findSlotForUpdate(UUID slotId) {
        return slotRepository.findForUpdateById(slotId)
            .orElseThrow(() -> new SlotNotFoundException(slotId));
    }

    private Page<Slot> findSlots(
            UUID calendarId,
            Instant from,
            Instant to,
            SlotStatus status,
            Pageable pageable) {

        if (status == null) {
            return slotRepository.findAllByCalendarAndTimeRange(
                    calendarId,
                    from,
                    to,
                    pageable
            );
        }
        return slotRepository.findAllByCalendarAndTimeRangeAndStatus(
                calendarId,
                from,
                to,
                status,
                pageable
        );
    }
}
