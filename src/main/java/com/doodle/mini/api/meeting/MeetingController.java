package com.doodle.mini.api.meeting;

import com.doodle.mini.service.meeting.MeetingService;
import com.doodle.mini.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping("/slots/{slotId}/meetings")
    public ResponseEntity<MeetingResponse> create(
        @PathVariable UUID slotId,
        @Valid @RequestBody CreateMeetingRequest request) {
        var response = meetingService.create(slotId, request);
        return ResponseEntity.created(URI.create("/api/v1/meetings/" + response.id())).body(response);
    }

    @GetMapping("/meetings/{meetingId}")
    public MeetingResponse findById(@PathVariable UUID meetingId) {
        return meetingService.findById(meetingId);
    }

    @GetMapping("/users/{userId}/meetings")
    public PageResponse<MeetingResponse> findAllByUser(
        @PathVariable UUID userId,
        @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        return meetingService.findAllByUserId(userId, pageable);
    }

    @PutMapping("/meetings/{meetingId}")
    public MeetingResponse update(
        @PathVariable UUID meetingId,
        @Valid @RequestBody UpdateMeetingRequest request) {
        return meetingService.update(meetingId, request);
    }

    @DeleteMapping("/meetings/{meetingId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID meetingId) {
        meetingService.cancel(meetingId);
        return ResponseEntity.noContent().build();
    }
}
