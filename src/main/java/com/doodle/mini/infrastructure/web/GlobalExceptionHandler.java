package com.doodle.mini.infrastructure.web;

import com.doodle.mini.service.user.exception.EmailAlreadyExistsException;
import com.doodle.mini.service.user.exception.InvalidTimezoneException;
import com.doodle.mini.service.user.exception.UserNotFoundException;
import com.doodle.mini.shared.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
        DataIntegrityViolationException exception,
        HttpServletRequest request) {

        log.warn("Data integrity violation. path={}", request.getRequestURI(), exception);
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The operation conflicts with existing data", request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiErrorResponse> handleLockingFailure(
        RuntimeException exception,
        HttpServletRequest request) {

        log.warn("Locking failure, concurrent modification detected. path={}", request.getRequestURI());
        return response(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified concurrently, please retry", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        log.error("Unexpected error processing request. method={}, path={}",
                request.getMethod(), request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> violations = new LinkedHashMap<>();
        ex.getBindingResult()
            .getFieldErrors()
            .forEach(error -> violations.putIfAbsent(error.getField(), error.getDefaultMessage()));

        String path = extractPath(request);
        ApiErrorResponse body = ApiErrorResponse.withViolations(
            status.value(), "VALIDATION_ERROR", "Request validation failed", path, violations);
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> violations = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            if (param == null) param = result.getMethodParameter().getParameterType().getSimpleName();
            String finalParam = param;
            result.getResolvableErrors().forEach(error ->
                violations.putIfAbsent(finalParam, error.getDefaultMessage())
            );
        });

        String path = extractPath(request);
        ApiErrorResponse body = ApiErrorResponse.withViolations(
            status.value(), "VALIDATION_ERROR", "Request validation failed", path, violations);
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String path = extractPath(request);
        String code = status.value() == 404 ? "RESOURCE_NOT_FOUND"
                    : status.value() == 405 ? "METHOD_NOT_ALLOWED"
                    : status.value() >= 500  ? "INTERNAL_SERVER_ERROR"
                    : "REQUEST_ERROR";
        String message = status.is5xxServerError() ? "An unexpected error occurred" : ex.getMessage();
        ApiErrorResponse apiError = ApiErrorResponse.of(status.value(), code, message, path);
        return ResponseEntity.status(status).headers(headers).body(apiError);
    }

    private static String extractPath(WebRequest request) {
        return request instanceof ServletWebRequest swr
                ? swr.getRequest().getRequestURI()
                : "";
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request) {

        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), code, message, request.getRequestURI()));
    }
}
