package com.supportflow.api.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = safeMessage(status, exception.getReason());
        String path = status == HttpStatus.NOT_FOUND ? null : request.getRequestURI();
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, path));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ValidationError> validationErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toValidationError)
                .toList();
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                request.getRequestURI(),
                validationErrors
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI());
        return ResponseEntity.badRequest().body(response);
    }

    private static ValidationError toValidationError(FieldError fieldError) {
        return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private static String safeMessage(HttpStatus status, String reason) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return "Unauthorized";
        }
        if (status == HttpStatus.CONFLICT) {
            return reason == null ? "Conflict" : reason;
        }
        return reason == null ? status.getReasonPhrase() : reason;
    }

    public record ErrorResponse(
            int status,
            String error,
            String message,
            String path,
            List<ValidationError> validationErrors
    ) {
        public static ErrorResponse of(HttpStatus status, String message) {
            return of(status, message, null, List.of());
        }

        public static ErrorResponse of(HttpStatus status, String message, String path) {
            return of(status, message, path, List.of());
        }

        public static ErrorResponse of(
                HttpStatus status,
                String message,
                String path,
                List<ValidationError> validationErrors
        ) {
            return new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, validationErrors);
        }
    }

    public record ValidationError(String field, String message) {
    }
}
