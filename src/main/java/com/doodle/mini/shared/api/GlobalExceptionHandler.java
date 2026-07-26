package com.doodle.mini.shared.api;

import com.doodle.mini.service.user.exception.EmailAlreadyExistsException;
import com.doodle.mini.service.user.exception.InvalidTimezoneException;
import com.doodle.mini.service.user.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
        UserNotFoundException exception,
        HttpServletRequest request) {

        log.warn("Request rejected because user was not found. path={}, message={}",
                request.getRequestURI(), exception.getMessage());
        return response(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(
        EmailAlreadyExistsException exception,
        HttpServletRequest request) {

        log.warn("Request rejected due to duplicated email. path={}", request.getRequestURI());
        return response(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidTimezoneException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTimezone(
        InvalidTimezoneException exception,
        HttpServletRequest request) {
        log.warn("Request rejected because timezone has a wrong format. path={}, message={}",
                request.getRequestURI(), exception.getMessage());
        return response(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request) {

        Map<String, String> violations = new LinkedHashMap<>();

        exception.getBindingResult()
            .getFieldErrors()
            .forEach(error -> violations.putIfAbsent(
                error.getField(),
                error.getDefaultMessage()
            ));

        ApiErrorResponse body = ApiErrorResponse.withViolations(
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_ERROR",
            "Request validation failed",
            request.getRequestURI(),
            violations
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        log.error("Unexpected error processing request. method={}, path={}",
                request.getMethod(), request.getRequestURI(), exception);

        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.of(
            status.value(),
            code,
            message,
            request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
