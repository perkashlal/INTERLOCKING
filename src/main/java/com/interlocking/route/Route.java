package com.interlocking.route;

import java.util.List;
import java.util.Map;

/**
 * A safe path through the layout (SRS Fig. 2 Route): the ordered track sections from
 * source to destination inclusive, and the throw position required for every point
 * (FR-08) the path passes through. Points where the route only touches one leg (the
 * path's own source or destination is the point itself) are not included, since no
 * two legs need to be connected there.
 */
public record Route(List<String> trackSectionIds, Map<String, PointPosition> pointPositions) {
}
