package com.doodle.mini.service.slot;

import com.doodle.mini.api.slot.CreateSlotRequest;
import com.doodle.mini.api.slot.SlotResponse;
import com.doodle.mini.api.slot.UpdateSlotRequest;
import com.doodle.mini.api.slot.UpdateSlotStatusRequest;
import com.doodle.mini.domain.slot.Slot;
import com.doodle.mini.domain.slot.SlotStatus;
import com.doodle.mini.domain.slot.TimeRange;
import com.doodle.mini.infrastructure.persistence.calendar.CalendarRepository;
import com.doodle.mini.infrastructure.persistence.slot.SlotRepository;
import com.doodle.mini.service.calendar.exception.CalendarNotFoundException;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

    private final CalendarRepository calendarRepository;
    private final SlotRepository slotRepository;

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

    @Transactional
    public SlotResponse update(UUID slotId, UpdateSlotRequest request) {
        validateTimeRange(request.startAt(), request.endAt());
        var timeRange = new TimeRange(request.startAt(), request.endAt());
        var calendarId = slotRepository.findCalendarIdById(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

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
