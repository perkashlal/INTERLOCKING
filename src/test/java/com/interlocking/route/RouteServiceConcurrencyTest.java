package com.interlocking.route;

import com.interlocking.model.LayoutGraph;
import com.interlocking.parser.XmlLayoutParser;
import com.interlocking.state.ScenarioStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the mutual-exclusion claim documented on {@link RouteService}: two
 * concurrent requests can never both reserve the same track section (NFR-01
 * Correctness under concurrent access, NFR-06 safety-first behavior). Single-user
 * per Table 8, but the dashboard/API can still receive overlapping in-flight
 * requests, so this is the concurrency counterpart to the sequential races already
 * covered in {@link RouteServiceTest}.
 */
class RouteServiceConcurrencyTest {

    private final XmlLayoutParser parser = new XmlLayoutParser();
    private ScenarioStateManager stateManager;
    private RouteService routeService;
    private LayoutGraph junctionLayout;

    @BeforeEach
    void setUp() throws IOException {
        stateManager = new ScenarioStateManager();
        routeService = new RouteService(new RouteFinder(), stateManager);
        junctionLayout = parser.parse(Files.newInputStream(Path.of("sample-layouts", "junction-layout.xml")));
    }

    @Test
    void concurrentRequestsForOverlappingRoutesLeaveExactlyOneWinner() throws Exception {
        stateManager.loadLayout(junctionLayout);
        stateManager.setInitialOccupancy(List.of("T1"));

        // Both routes start at T1 and must cross the shared T2/P1 neck before
        // splitting toward T5 or T6, so they contend for the same resources.
        List<RouteOutcome> outcomes = runConcurrently(
                () -> routeService.findAndReserveRoute("T1", "T5"),
                () -> routeService.findAndReserveRoute("T1", "T6"));

        long successes = outcomes.stream().filter(RouteOutcome::succeeded).count();
        long failures = outcomes.stream().filter(o -> !o.succeeded()).count();
        assertThat(successes).as("exactly one of two contending requests should win").isEqualTo(1);
        assertThat(failures).isEqualTo(1);

        RouteOutcome winner = outcomes.stream().filter(RouteOutcome::succeeded).findFirst().orElseThrow();
        RouteOutcome loser = outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow();
        assertThat(loser.failure()).isInstanceOf(IllegalStateException.class);

        // The state left behind must match the winner's route exactly: nothing
        // from the loser's attempt, nothing double-reserved, nothing missing.
        assertThat(stateManager.reservedTracks())
                .containsExactlyInAnyOrderElementsOf(winner.result().reservedTrackIds());
    }

    @Test
    void concurrentRequestsForDisjointRoutesBothSucceed() throws Exception {
        stateManager.loadLayout(junctionLayout);
        stateManager.setInitialOccupancy(List.of("T3", "T4"));

        // T3->T5 and T4->T6 share no track sections or points, so both requests
        // should be granted without either blocking the other.
        List<RouteOutcome> outcomes = runConcurrently(
                () -> routeService.findAndReserveRoute("T3", "T5"),
                () -> routeService.findAndReserveRoute("T4", "T6"));

        assertThat(outcomes).allMatch(RouteOutcome::succeeded);
        assertThat(stateManager.reservedTracks()).containsExactlyInAnyOrder("T5", "T6");
    }

    @Test
    void manyThreadsRacingForTheSameSingleTrackRouteYieldExactlyOneWinner() throws Exception {
        LayoutGraph simpleLine = parser.parse(Files.newInputStream(Path.of("sample-layouts", "simple-line.xml")));
        stateManager.loadLayout(simpleLine);
        stateManager.setInitialOccupancy(List.of("T1"));

        int contenders = 16;
        List<Callable<RouteResult>> tasks = IntStream.range(0, contenders)
                .<Callable<RouteResult>>mapToObj(i -> () -> routeService.findAndReserveRoute("T1", "T5"))
                .toList();

        List<RouteOutcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes.stream().filter(RouteOutcome::succeeded).count())
                .as("only one of %d racing identical requests should win", contenders)
                .isEqualTo(1);
        assertThat(stateManager.reservedTracks()).containsExactlyInAnyOrder("T2", "T3", "T4", "T5");
    }

    private List<RouteOutcome> runConcurrently(Callable<RouteResult>... tasks) throws Exception {
        return runConcurrently(List.of(tasks));
    }

    private List<RouteOutcome> runConcurrently(List<Callable<RouteResult>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);

            List<Future<RouteOutcome>> futures = new ArrayList<>();
            for (Callable<RouteResult> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return RouteOutcome.success(task.call());
                    } catch (Exception e) {
                        return RouteOutcome.failure(e);
                    }
                }));
            }

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            List<RouteOutcome> outcomes = new ArrayList<>();
            for (Future<RouteOutcome> future : futures) {
                outcomes.add(future.get(5, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private record RouteOutcome(RouteResult result, Exception failure) {
        static RouteOutcome success(RouteResult result) {
            return new RouteOutcome(result, null);
        }

        static RouteOutcome failure(Exception failure) {
            return new RouteOutcome(null, failure);
        }

        boolean succeeded() {
            return result != null;
        }
    }
}
