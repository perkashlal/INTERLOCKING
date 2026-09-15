package com.interlocking.model;

/**
 * trackSection "type" attribute. POINT sections are the layout's branching
 * resources (SRS Table 6 "Point / switch") — in this XML format a point is not
 * a separate element, it is a trackSection with type="point" whose three
 * neighbors carry side="plus"/"minus"/"stem". Any other/unrecognized type is
 * treated as ordinary track (OTHER) rather than rejecting the file, since the
 * SRS only requires the layout to be legal, not to enumerate every type value.
 */
public enum TrackSectionType {
    LINEAR,
    POINT,
    OTHER;

    public static TrackSectionType fromXml(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return LINEAR;
        }
        return switch (rawValue.trim().toLowerCase()) {
            case "linear" -> LINEAR;
            case "point" -> POINT;
            default -> OTHER;
        };
    }
}
