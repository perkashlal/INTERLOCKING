package com.interlocking.api.dto;

/** Structured error body returned instead of a stack trace (FR-18, NFR-02). */
public record ApiError(String message) {
}
