package com.interlocking.route;

/**
 * The throw position a point/switch (SRS Table 6) must be locked to for a route to
 * pass through it safely: PLUS connects the stem to the "plus" neighbor, MINUS
 * connects the stem to the "minus" neighbor. A route can never require both at once,
 * since that would mean crossing directly between the plus and minus legs without
 * going through the stem, which is not physically possible.
 */
public enum PointPosition {
    PLUS,
    MINUS;

    static PointPosition fromSide(String side) {
        return switch (side) {
            case "plus" -> PLUS;
            case "minus" -> MINUS;
            default -> throw new IllegalArgumentException("Not a point throw side: '" + side + "'");
        };
    }
}
