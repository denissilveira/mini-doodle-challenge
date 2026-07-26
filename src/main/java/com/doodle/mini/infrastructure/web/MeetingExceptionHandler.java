package com.doodle.mini.infrastructure.web;

import com.doodle.mini.domain.meeting.exception.OrganizerCannotBeParticipantException;
import com.doodle.mini.service.meeting.exception.MeetingNotFoundException;
import com.doodle.mini.service.meeting.exception.MeetingParticipantsNotFoundException;
import com.doodle.mini.service.meeting.exception.SlotNotAvailableForMeetingException;
import com.doodle.mini.shared.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MeetingExceptionHandler {

    @ExceptionHandler(MeetingNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMeetingNotFound(
        MeetingNotFoundException exception,
        HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND", exception, request);
    }

    @ExceptionHandler(SlotNotAvailableForMeetingException.class)
    public ResponseEntity<ApiErrorResponse> handleSlotNotAvailable(
        SlotNotAvailableForMeetingException exception,
        HttpServletRequest request) {
        log.warn("Meeting creation rejected because slot is unavailable. path={}, message={}",
            request.getRequestURI(), exception.getMessage());
        return response(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", exception, request);
    }

    @ExceptionHandler(MeetingParticipantsNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleParticipantsNotFound(
        MeetingParticipantsNotFoundException exception,
        HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MEETING_PARTICIPANTS_NOT_FOUND", exception, request);
    }

    @ExceptionHandler(OrganizerCannotBeParticipantException.class)
    public ResponseEntity<ApiErrorResponse> handleOrganizerAsParticipant(
        OrganizerCannotBeParticipantException exception,
        HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "ORGANIZER_CANNOT_BE_PARTICIPANT", exception, request);
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String code,
        RuntimeException exception,
        HttpServletRequest request) {
        var body = ApiErrorResponse.of(status.value(), code, exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
