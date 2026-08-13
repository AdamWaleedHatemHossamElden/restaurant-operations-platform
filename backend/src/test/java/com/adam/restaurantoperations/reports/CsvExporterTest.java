package com.adam.restaurantoperations.reports;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExporterTest {
    private final CsvExporter exporter = new CsvExporter();

    @Test
    void quotesSpecialCharactersAndNeutralizesFormulaPrefixes() {
        String csv = new String(exporter.rows(List.of(
                List.of("value", "safe"),
                List.of("=SUM(1,2)", "+cmd"),
                List.of("-1+2", "@lookup"),
                List.of("comma,value", "quote\"value"),
                List.of("line\nbreak", "plain"))), StandardCharsets.UTF_8);

        assertThat(csv)
                .contains("\"'=SUM(1,2)\"")
                .contains("\"'+cmd\"")
                .contains("\"'-1+2\"")
                .contains("\"'@lookup\"")
                .contains("\"comma,value\"")
                .contains("\"quote\"\"value\"")
                .contains("\"line\nbreak\"");
    }
}
