package com.doodle.mini.infrastructure.web;

import com.doodle.mini.service.calendar.exception.CalendarNotFoundException;
import com.doodle.mini.service.slot.exception.*;
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
public class SlotExceptionHandler {

    @ExceptionHandler({
        SlotNotFoundException.class,
        CalendarNotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleNotFound(
        RuntimeException exception,
        HttpServletRequest request) {
        log.debug("Scheduling resource not found. path={}, message={}", request.getRequestURI(), exception.getMessage());
        return response(HttpStatus.NOT_FOUND, "SCHEDULING_RESOURCE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidSlotTimeRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRange(
        InvalidSlotTimeRangeException exception,
        HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_SLOT_TIME_RANGE", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidSlotStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStatus(
        InvalidSlotStatusException exception,
        HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_SLOT_STATUS", exception.getMessage(), request);
    }

    @ExceptionHandler({
        SlotOverlapException.class,
        BookedSlotOperationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(
        RuntimeException exception,
        HttpServletRequest request) {
        log.warn("Slot operation rejected. path={}, message={}", request.getRequestURI(), exception.getMessage());
        return response(HttpStatus.CONFLICT, "SLOT_OPERATION_CONFLICT", exception.getMessage(), request);
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), code, message, request.getRequestURI()));
    }
}
