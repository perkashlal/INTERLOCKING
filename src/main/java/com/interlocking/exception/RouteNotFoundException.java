package com.interlocking.exception;

/**
 * Thrown when no safe route can be found between two track sections: the ids don't
 * resolve, no path exists in the layout topology, or every candidate path is blocked
 * by an occupied/reserved section (NFR-01) or an illegal point traversal (FR-08).
 */
public class RouteNotFoundException extends RuntimeException {

    public RouteNotFoundException(String message) {
        super(message);
    }
}
