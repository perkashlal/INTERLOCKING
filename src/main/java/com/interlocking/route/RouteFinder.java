package com.interlocking.route;

import com.interlocking.exception.RouteNotFoundException;
import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Neighbor;
import com.interlocking.model.TrackSection;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds a safe path through the layout's connectivity graph (SRS Fig. 2 RouteFinder).
 * Pathfinding is pure/stateless: callers supply which track sections currently count
 * as usable (NFR-01), and reservation of the result is a separate concern (see
 * {@link RouteReservationService}).
 *
 * <p>Two safety rules beyond plain shortest-path apply:
 * <ul>
 *   <li>every track section on the path, source and destination included, must be
 *       usable per the supplied predicate;</li>
 *   <li>a path may only pass through a point (SRS "Point / switch") between its stem
 *       and one of its plus/minus legs, never directly between the plus and minus
 *       legs (FR-08) — that would mean the point is thrown to both positions at once,
 *       which isn't physically possible.</li>
 * </ul>
 */
@Component
public class RouteFinder {

    /**
     * @param layout  the loaded layout topology to search
     * @param isUsable track sections this route is allowed to pass through (NFR-01)
     * @param fromId  source track section id (already resolved, not a markerboard id)
     * @param toId    destination track section id (already resolved)
     */
    public Route findRoute(LayoutGraph layout, Predicate<String> isUsable, String fromId, String toId) {
        if (layout.trackSection(fromId).isEmpty()) {
            throw new RouteNotFoundException("Unknown source track section: " + fromId);
        }
        if (layout.trackSection(toId).isEmpty()) {
            throw new RouteNotFoundException("Unknown destination track section: " + toId);
        }
        if (!isUsable.test(fromId)) {
            throw new RouteNotFoundException("Source track section is not free: " + fromId);
        }
        if (!isUsable.test(toId)) {
            throw new RouteNotFoundException("Destination track section is not free: " + toId);
        }

        if (fromId.equals(toId)) {
            return new Route(List.of(fromId), Map.of());
        }

        List<String> path = shortestFreePath(layout, isUsable, fromId, toId);
        if (path == null) {
            throw new RouteNotFoundException("No safe route found from '" + fromId + "' to '" + toId + "'");
        }

        return new Route(path, pointPositionsAlong(layout, path));
    }

    /** Breadth-first search so the returned route uses the fewest track sections. */
    private List<String> shortestFreePath(LayoutGraph layout, Predicate<String> isUsable, String fromId, String toId) {
        Map<String, String> cameFrom = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        visited.add(fromId);
        queue.add(fromId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            TrackSection current = layout.trackSection(currentId).orElseThrow();
            String arrivedFromId = cameFrom.get(currentId);

            for (String neighborId : current.neighborIds()) {
                if (visited.contains(neighborId) || !isUsable.test(neighborId)) {
                    continue;
                }
                if (current.isPoint() && arrivedFromId != null && !isValidPointTransition(current, arrivedFromId, neighborId)) {
                    continue;
                }
                visited.add(neighborId);
                cameFrom.put(neighborId, currentId);
                if (neighborId.equals(toId)) {
                    return reconstructPath(cameFrom, fromId, toId);
                }
                queue.add(neighborId);
            }
        }
        return null;
    }

    /**
     * A point may only be crossed between its stem and one plus/minus leg: exactly
     * one of the arrival/departure sides must be "stem", never both or neither.
     */
    private boolean isValidPointTransition(TrackSection point, String arrivedFromId, String nextId) {
        boolean arrivalIsStem = "stem".equals(sideOf(point, arrivedFromId));
        boolean nextIsStem = "stem".equals(sideOf(point, nextId));
        return arrivalIsStem != nextIsStem;
    }

    private String sideOf(TrackSection point, String neighborId) {
        for (Neighbor neighbor : point.neighbors()) {
            if (neighbor.ref().equals(neighborId)) {
                return neighbor.side() == null ? "" : neighbor.side().trim().toLowerCase();
            }
        }
        return "";
    }

    private List<String> reconstructPath(Map<String, String> cameFrom, String fromId, String toId) {
        LinkedList<String> path = new LinkedList<>();
        String cursor = toId;
        while (cursor != null) {
            path.addFirst(cursor);
            if (cursor.equals(fromId)) {
                break;
            }
            cursor = cameFrom.get(cursor);
        }
        return path;
    }

    /**
     * Required throw position for every point the path passes through, i.e. every
     * point that isn't the path's own first or last section.
     */
    private Map<String, PointPosition> pointPositionsAlong(LayoutGraph layout, List<String> path) {
        Map<String, PointPosition> positions = new LinkedHashMap<>();
        for (int i = 1; i < path.size() - 1; i++) {
            String id = path.get(i);
            TrackSection section = layout.trackSection(id).orElseThrow();
            if (!section.isPoint()) {
                continue;
            }
            String incomingSide = sideOf(section, path.get(i - 1));
            String outgoingSide = sideOf(section, path.get(i + 1));
            String throwSide = "stem".equals(incomingSide) ? outgoingSide : incomingSide;
            positions.put(id, PointPosition.fromSide(throwSide));
        }
        return positions;
    }
}
