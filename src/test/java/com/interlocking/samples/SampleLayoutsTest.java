package com.interlocking.samples;

import com.interlocking.exception.LayoutParseException;
import com.interlocking.parser.XmlLayoutParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Guards the sample layouts shipped for the dashboard and README walkthrough. */
class SampleLayoutsTest {

    private final XmlLayoutParser parser = new XmlLayoutParser();

    @Test
    void validSamplesAllLoad() throws IOException {
        for (String name : new String[] {"simple-line.xml", "junction-layout.xml", "lvr_1.xml"}) {
            try (InputStream in = Files.newInputStream(Path.of("sample-layouts", name))) {
                assertThat(parser.parse(in).allTrackSections()).as(name).isNotEmpty();
            }
        }
    }

    @Test
    void malformedDashboardSampleIsRejected() throws IOException {
        try (InputStream in = Files.newInputStream(
                Path.of("src/main/resources/static/samples/malformed.xml"))) {
            assertThatThrownBy(() -> parser.parse(in)).isInstanceOf(LayoutParseException.class);
        }
    }

    @Test
    void dashboardSamplesMatchRootSampleLayouts() throws IOException {
        for (String name : new String[] {"simple-line.xml", "junction-layout.xml", "malformed.xml"}) {
            assertThat(Files.readString(Path.of("src/main/resources/static/samples", name)).strip())
                    .as(name)
                    .isEqualTo(Files.readString(Path.of("sample-layouts", name)).strip());
        }
    }
}
