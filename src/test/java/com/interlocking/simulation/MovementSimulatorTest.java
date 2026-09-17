package com.interlocking.simulation;

import com.interlocking.model.LayoutGraph;
import com.interlocking.parser.XmlLayoutParser;
import com.interlocking.state.ScenarioStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovementSimulatorTest {

    private final XmlLayoutParser parser = new XmlLayoutParser();
    private ScenarioStateManager state;
    private MovementSimulator simulator;

    @BeforeEach
    void setUp() throws IOException {
        state = new ScenarioStateManager();
        simulator = new MovementSimulator(state);
        LayoutGraph simpleLine = parser.parse(Files.newInputStream(Path.of("sample-layouts", "simple-line.xml")));
        state.loadLayout(simpleLine);
    }

    @Test
    void movesTrainToDestinationAndFreesEverythingBehindIt() {
        state.setInitialOccupancy(List.of("T1"));
        state.reserve(List.of("T2", "T3", "T4", "T5"));

        simulator.simulate(List.of("T1", "T2", "T3", "T4", "T5"));

        assertThat(state.isOccupied("T5")).isTrue();
        assertThat(state.isFree("T1")).isTrue();
        assertThat(state.isFree("T2")).isTrue();
        assertThat(state.isFree("T3")).isTrue();
        assertThat(state.isFree("T4")).isTrue();
        assertThat(state.reservedTracks()).isEmpty();
    }

    @Test
    void refusesToSimulateWhenAnIntermediateSectionWasNeverReserved() {
        state.setInitialOccupancy(List.of("T1"));
        state.reserve(List.of("T2", "T4", "T5")); // T3 missing

        assertThatThrownBy(() -> simulator.simulate(List.of("T1", "T2", "T3", "T4", "T5")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("T3");

        // Nothing should have moved as a result of the rejected attempt.
        assertThat(state.isOccupied("T1")).isTrue();
        assertThat(state.reservedTracks()).containsExactlyInAnyOrder("T2", "T4", "T5");
    }

    @Test
    void rejectsEmptyOrNullPath() {
        assertThatThrownBy(() -> simulator.simulate(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> simulator.simulate(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
