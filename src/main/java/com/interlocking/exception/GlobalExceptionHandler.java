package com.interlocking.exception;

import com.interlocking.api.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns parse/state failures into structured JSON errors instead of a stack trace
 * (FR-18: "reject it and report a clear error").
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Unexpected server error: " + e.getMessage()));
    }
}
