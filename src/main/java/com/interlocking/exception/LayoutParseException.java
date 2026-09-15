package com.interlocking.exception;

/**
 * Thrown when a supplied XML layout file is not well-formed, fails schema validation,
 * or fails referential-integrity checks (FR-18: reject instead of loading a partial
 * or corrupt layout).
 */
public class LayoutParseException extends RuntimeException {

    public LayoutParseException(String message) {
        super(message);
    }

    public LayoutParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
