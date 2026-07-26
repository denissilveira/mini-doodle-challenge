package com.doodle.mini.api.meeting;

import com.doodle.mini.service.meeting.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping("/api/v1/slots/{slotId}/meetings")
    public ResponseEntity<MeetingResponse> create(
        @PathVariable UUID slotId,
        @Valid @RequestBody CreateMeetingRequest request) {
        var response = meetingService.create(slotId, request);
        return ResponseEntity.created(URI.create("/api/v1/meetings/" + response.id())).body(response);
    }

    @GetMapping("/api/v1/meetings/{meetingId}")
    public MeetingResponse findById(@PathVariable UUID meetingId) {
        return meetingService.findById(meetingId);
    }

    @PutMapping("/api/v1/meetings/{meetingId}")
    public MeetingResponse update(
        @PathVariable UUID meetingId,
        @Valid @RequestBody UpdateMeetingRequest request) {
        return meetingService.update(meetingId, request);
    }

    @DeleteMapping("/api/v1/meetings/{meetingId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID meetingId) {
        meetingService.cancel(meetingId);
        return ResponseEntity.noContent().build();
    }
}
