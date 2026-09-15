package com.interlocking.model;

import java.util.List;

/**
 * A branching track resource (SRS Table 6) that must be reserved together with any
 * route passing through one of its covered track sections.
 */
public record Point(String id, List<String> trackSectionIds) {
}
