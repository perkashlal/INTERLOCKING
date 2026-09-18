package com.interlocking.route;

import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Neighbor;
import com.interlocking.model.TrackSection;
import com.interlocking.model.TrackSectionType;
import com.interlocking.state.ScenarioStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteServiceTest {

    private final ScenarioStateManager stateManager = new ScenarioStateManager();
    private final RouteService routeService = new RouteService(new RouteFinder(), stateManager);

    @BeforeEach
    void loadLinearLayout() {
        Map<String, TrackSection> sections = new LinkedHashMap<>();
        sections.put("T1", new TrackSection("T1", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "down"))));
        sections.put("T2", new TrackSection("T2", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("T1", "up"), new Neighbor("T3", "down"))));
        sections.put("T3", new TrackSection("T3", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "up"))));
        stateManager.loadLayout(new LayoutGraph(sections, Map.of()));
        stateManager.setInitialOccupancy(List.of("T1"));
    }

    @Test
    void findsAndReservesRouteExcludingTheOccupiedOrigin() {
        RouteResult result = routeService.findAndReserveRoute("T1", "T3");

        assertThat(result.path()).containsExactly("T1", "T2", "T3");
        assertThat(result.reservedTrackIds()).containsExactly("T2", "T3");
        assertThat(stateManager.isReserved("T2")).isTrue();
        assertThat(stateManager.isReserved("T3")).isTrue();
        assertThat(stateManager.isOccupied("T1")).isTrue();
    }

    @Test
    void secondRouteCannotReuseAnAlreadyReservedSection() {
        routeService.findAndReserveRoute("T1", "T3");

        assertThatThrownBy(() -> routeService.findAndReserveRoute("T3", "T1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No available route");
    }

    @Test
    void rejectsUnknownOriginOrDestination() {
        assertThatThrownBy(() -> routeService.findAndReserveRoute("GHOST", "T3"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> routeService.findAndReserveRoute("T1", "GHOST"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSameOriginAndDestination() {
        assertThatThrownBy(() -> routeService.findAndReserveRoute("T1", "T1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresLayoutToBeLoaded() {
        RouteService fresh = new RouteService(new RouteFinder(), new ScenarioStateManager());

        assertThatThrownBy(() -> fresh.findAndReserveRoute("T1", "T2"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * NFR-01/safety-first: two trains must never both hold the same track section.
     * RouteService documents that finding and reserving a route is atomic under
     * concurrent access (synchronized on the shared state manager); this proves it
     * by firing many simultaneous requests for the one route T1-T2-T3 and checking
     * that exactly one of them wins the reservation and every other one is rejected,
     * with no double-reservation and no corrupted state left behind.
     */
    @Test
    void concurrentRequestsForTheSameRouteReserveItExactlyOnce() throws Exception {
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    routeService.findAndReserveRoute("T1", "T3");
                    successes.incrementAndGet();
                } catch (IllegalStateException expectedWhenAlreadyReserved) {
                    rejections.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(threadCount - 1);
        assertThat(stateManager.isReserved("T2")).isTrue();
        assertThat(stateManager.isReserved("T3")).isTrue();
    }
}
