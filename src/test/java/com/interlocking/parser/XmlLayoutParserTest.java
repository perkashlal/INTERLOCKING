package com.interlocking.parser;

import com.interlocking.exception.LayoutParseException;
import com.interlocking.model.LayoutGraph;
import com.interlocking.model.TrackSection;
import com.interlocking.model.TrackSectionType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlLayoutParserTest {

    private final XmlLayoutParser parser = new XmlLayoutParser();

    @Test
    void parsesSimpleLinearLayout() throws IOException {
        LayoutGraph graph = parser.parse(sampleLayout("simple-line.xml"));

        assertThat(graph.allTrackSections()).hasSize(5);
        assertThat(graph.hasTrackSection("T1")).isTrue();
        assertThat(graph.neighborsOf("T1")).containsExactly("T2");
        assertThat(graph.neighborsOf("T3")).containsExactlyInAnyOrder("T2", "T4");
        assertThat(graph.isPoint("T1")).isFalse();
        assertThat(graph.resolveTrackSectionId("M1")).contains("T1");
    }

    @Test
    void parsesJunctionLayoutAndIdentifiesPoints() throws IOException {
        LayoutGraph graph = parser.parse(sampleLayout("junction-layout.xml"));

        assertThat(graph.allTrackSections()).hasSize(7);
        assertThat(graph.isPoint("P1")).isTrue();
        assertThat(graph.neighborsOf("P1")).containsExactlyInAnyOrder("T2", "T3", "T4");

        TrackSection point = graph.trackSection("P1").orElseThrow();
        assertThat(point.type()).isEqualTo(TrackSectionType.POINT);
    }

    @Test
    void parsesRealExportedLayout() throws IOException {
        LayoutGraph graph = parser.parse(sampleLayout("lvr_1.xml"));

        assertThat(graph.hasTrackSection("533")).isTrue();
        assertThat(graph.hasTrackSection("PM01U")).isTrue();
        assertThat(graph.isPoint("PM01U")).isTrue();
        assertThat(graph.neighborsOf("PM01U")).containsExactlyInAnyOrder("533", "534", "083");
        assertThat(graph.resolveTrackSectionId("LU11")).contains("533");
    }

    @Test
    void rejectsMalformedXmlWithoutLoadingPartialLayout() throws IOException {
        assertThatThrownBy(() -> parser.parse(sampleLayout("malformed.xml")))
                .isInstanceOf(LayoutParseException.class);
    }

    @Test
    void rejectsLayoutWithUnknownNeighborReference() {
        String xml = """
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/2.4.1">
                  <interlocking id="ixl">
                    <network id="net">
                      <trackSection id="T1" length="100" type="linear">
                        <neighbor ref="GHOST" side="down"/>
                      </trackSection>
                    </network>
                  </interlocking>
                </xmi:XMI>
                """;

        assertThatThrownBy(() -> parser.parse(asStream(xml)))
                .isInstanceOf(LayoutParseException.class)
                .hasMessageContaining("GHOST");
    }

    @Test
    void rejectsLayoutWithUnknownMarkerboardTrack() {
        String xml = """
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/2.4.1">
                  <interlocking id="ixl">
                    <network id="net">
                      <trackSection id="T1" length="100" type="linear"/>
                      <markerboard id="M1" mounted="up" track="MISSING" distance="10.0"/>
                    </network>
                  </interlocking>
                </xmi:XMI>
                """;

        assertThatThrownBy(() -> parser.parse(asStream(xml)))
                .isInstanceOf(LayoutParseException.class)
                .hasMessageContaining("MISSING");
    }

    @Test
    void rejectsDuplicateTrackSectionIds() {
        String xml = """
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/2.4.1">
                  <interlocking id="ixl">
                    <network id="net">
                      <trackSection id="T1" length="100" type="linear"/>
                      <trackSection id="T1" length="50" type="linear"/>
                    </network>
                  </interlocking>
                </xmi:XMI>
                """;

        assertThatThrownBy(() -> parser.parse(asStream(xml)))
                .isInstanceOf(LayoutParseException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsLayoutWithNoTrackSections() {
        String xml = """
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/2.4.1">
                  <interlocking id="ixl">
                    <network id="net"/>
                  </interlocking>
                </xmi:XMI>
                """;

        assertThatThrownBy(() -> parser.parse(asStream(xml)))
                .isInstanceOf(LayoutParseException.class);
    }

    private InputStream sampleLayout(String fileName) throws IOException {
        Path path = Path.of("sample-layouts", fileName);
        return Files.newInputStream(path);
    }

    private InputStream asStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
