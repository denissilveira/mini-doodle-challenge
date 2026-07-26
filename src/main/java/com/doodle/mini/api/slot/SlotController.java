package com.doodle.mini.api.slot;

import com.doodle.mini.domain.slot.SlotStatus;
import com.doodle.mini.service.slot.SlotService;
import com.doodle.mini.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SlotController {

    private final SlotService slotService;

    @PostMapping("/users/{userId}/slots")
    public ResponseEntity<SlotResponse> create(
        @PathVariable UUID userId,
        @Valid @RequestBody CreateSlotRequest request) {
        SlotResponse response = slotService.create(userId, request);

        return ResponseEntity
            .created(URI.create("/api/v1/slots/" + response.id()))
            .body(response);
    }

    @GetMapping("/slots/{slotId}")
    public SlotResponse findById(
        @PathVariable UUID slotId) {
        return slotService.findById(slotId);
    }

    @GetMapping("/users/{userId}/slots")
    public PageResponse<SlotResponse> findAll(
        @PathVariable UUID userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) SlotStatus status,
        @PageableDefault(size = 50, sort = "timeRange.startAt")
        Pageable pageable) {
        return slotService.findAll(
            userId,
            from,
            to,
            status,
            pageable
        );
    }

    @GetMapping("/users/{userId}/slots/availability")
    public SlotAvailabilityResponse getAvailability(
        @PathVariable UUID userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return slotService.getAvailability(userId, from, to);
    }

    @GetMapping("/users/{userId}/slots/summary")
    public SlotAvailabilitySummaryResponse summarize(
        @PathVariable UUID userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return slotService.summarize(userId, from, to);
    }

    @PutMapping("/slots/{slotId}")
    public SlotResponse update(
        @PathVariable UUID slotId,
        @Valid @RequestBody UpdateSlotRequest request) {
        return slotService.update(slotId, request);
    }

    @PatchMapping("/slots/{slotId}/status")
    public SlotResponse updateStatus(
        @PathVariable UUID slotId,
        @Valid @RequestBody UpdateSlotStatusRequest request) {
        return slotService.updateStatus(slotId, request);
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID slotId) {
        slotService.delete(slotId);
        return ResponseEntity.noContent().build();
    }
}
