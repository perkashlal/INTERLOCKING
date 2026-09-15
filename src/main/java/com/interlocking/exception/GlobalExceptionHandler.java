package com.interlocking.exception;

import com.interlocking.api.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns parse/state failures into structured JSON errors instead of a stack trace
 * (FR-18: "reject it and report a clear error"). Framework-level "not found" cases
 * (e.g. hitting "/" before the dashboard exists) are left as ordinary 404s rather
 * than being swallowed by the catch-all as a misleading 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LayoutParseException.class)
    public ResponseEntity<ApiError> handleLayoutParse(LayoutParseException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("Not found: " + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Unexpected server error: " + e.getMessage()));
    }
}
