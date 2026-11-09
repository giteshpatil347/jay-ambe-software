package com.materialbook.common;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }

    /** Per-field / per-item details rendered into the problem response; empty unless a subclass supplies them. */
    public List<GlobalExceptionHandler.FieldError> getFieldErrors() { return List.of(); }
    public HttpStatus getStatus() { return status; }

    public static class NotFoundException extends ApiException {
        public NotFoundException(String message) { super("NOT_FOUND", HttpStatus.NOT_FOUND, message); }
    }

    public static class ConflictException extends ApiException {
        public ConflictException(String message) { super("CONFLICT", HttpStatus.CONFLICT, message); }
    }

    public static class InsufficientStockException extends ApiException {
        private final List<GlobalExceptionHandler.FieldError> fieldErrors;

        public InsufficientStockException(String message) { this(message, List.of()); }

        /** {@code fieldErrors} carry one entry per short product: field = product code, message = shortfall text. */
        public InsufficientStockException(String message, List<GlobalExceptionHandler.FieldError> fieldErrors) {
            super("INSUFFICIENT_STOCK", HttpStatus.CONFLICT, message);
            this.fieldErrors = fieldErrors;
        }

        @Override
        public List<GlobalExceptionHandler.FieldError> getFieldErrors() { return fieldErrors; }
    }

    public static class ValidationException extends ApiException {
        public ValidationException(String message) { super("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY, message); }
    }
}
