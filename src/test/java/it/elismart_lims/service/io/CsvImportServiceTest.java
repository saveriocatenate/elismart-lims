package it.elismart_lims.service.io;

import it.elismart_lims.dto.CsvFormat;
import it.elismart_lims.dto.CsvImportConfig;
import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.dto.WellMapping;
import it.elismart_lims.model.PairType;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link CsvImportService} — GENERIC format parsing and error cases.
 *
 * <p>All tests use in-memory CSV strings wrapped in {@link ByteArrayInputStream}.
 * No Spring context is loaded; this is a plain unit test.</p>
 */
class CsvImportServiceTest {

    private CsvImportService service;

    /** Standard column names used across tests. */
    private static final String WELL_COL    = "WellId";
    private static final String SIGNAL1_COL = "Signal1";
    private static final String SIGNAL2_COL = "Signal2";

    @BeforeEach
    void setUp() {
        service = new CsvImportService();
    }

    // -------------------------------------------------------------------------
    // Happy-path: 6-row generic CSV → 6 pairs
    // -------------------------------------------------------------------------

    /**
     * A CSV with 6 data rows and all 6 wells mapped produces exactly 6
     * {@link MeasurementPairRequest}s with the correct signal values, pair types,
     * and nominal concentrations.
     */
    @Test
    @DisplayName("GENERIC CSV with 6 mapped rows returns 6 MeasurementPairRequests")
    void parse_genericFormat_sixRows_returns6Pairs() throws IOException {
        String csv = String.join("\n",
                "WellId,Signal1,Signal2",
                "A1,0.100,0.110",   // CALIBRATION conc=1.0
                "A2,0.200,0.210",   // CALIBRATION conc=2.0
                "A3,0.300,0.310",   // CALIBRATION conc=4.0
                "B1,0.500,0.510",   // CONTROL     conc=3.0
                "C1,0.600,0.610",   // SAMPLE      conc=null
                "C2,0.700,0.710"    // SAMPLE      conc=null
        );

        CsvImportConfig config = getImportConfig();

        List<MeasurementPairRequest> result = service.parse(toStream(csv), config);

        assertThat(result).hasSize(6);

        // Verify first row
        MeasurementPairRequest first = result.getFirst();
        assertThat(first.pairType()).isEqualTo(PairType.CALIBRATION);
        assertThat(first.concentrationNominal()).isEqualTo(1.0);
        assertThat(first.signal1()).isCloseTo(0.100, within(1e-6));
        assertThat(first.signal2()).isCloseTo(0.110, within(1e-6));
        assertThat(first.isOutlier()).isFalse();
        assertThat(first.recoveryPct()).isNull();

        // Verify sample rows
        MeasurementPairRequest sampleRow = result.get(4);
        assertThat(sampleRow.pairType()).isEqualTo(PairType.SAMPLE);
        assertThat(sampleRow.concentrationNominal()).isNull();
        assertThat(sampleRow.signal1()).isCloseTo(0.600, within(1e-6));
        assertThat(sampleRow.signal2()).isCloseTo(0.610, within(1e-6));
    }

    private static @NonNull CsvImportConfig getImportConfig() {
        Map<String, WellMapping> mapping = Map.of(
                "A1", new WellMapping(PairType.CALIBRATION, 1.0),
                "A2", new WellMapping(PairType.CALIBRATION, 2.0),
                "A3", new WellMapping(PairType.CALIBRATION, 4.0),
                "B1", new WellMapping(PairType.CONTROL, 3.0),
                "C1", new WellMapping(PairType.SAMPLE, null),
                "C2", new WellMapping(PairType.SAMPLE, null)
        );
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, mapping);
        return config;
    }

    /**
     * A signal1 and signal2 from a known row must be parsed to the exact expected double values.
     */
    @Test
    @DisplayName("signal values are parsed with full precision")
    void parse_genericFormat_signalsHaveCorrectValues() throws IOException {
        String csv = "WellId,Signal1,Signal2\nD1,1.2345,9.8765";
        Map<String, WellMapping> mapping = Map.of("D1", new WellMapping(PairType.CONTROL, 5.0));
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, mapping);

        List<MeasurementPairRequest> result = service.parse(toStream(csv), config);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().signal1()).isEqualTo(1.2345);
        assertThat(result.getFirst().signal2()).isEqualTo(9.8765);
    }

    /**
     * Rows whose well ID is not in the mapping must be silently skipped.
     * The result should contain only the 2 mapped rows out of 4 total.
     */
    @Test
    @DisplayName("unmapped wells are silently skipped")
    void parse_genericFormat_unmappedWellsSkipped() throws IOException {
        String csv = String.join("\n",
                "WellId,Signal1,Signal2",
                "A1,0.1,0.2",   // mapped
                "Z9,9.9,9.8",   // NOT in mapping
                "B1,0.3,0.4",   // mapped
                "Z8,8.8,8.7"    // NOT in mapping
        );
        Map<String, WellMapping> mapping = Map.of(
                "A1", new WellMapping(PairType.CALIBRATION, 1.0),
                "B1", new WellMapping(PairType.CONTROL, 2.0)
        );
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, mapping);

        List<MeasurementPairRequest> result = service.parse(toStream(csv), config);

        assertThat(result).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    /**
     * An empty file (0 bytes) must throw {@link IllegalArgumentException} with a
     * message that explains the file is empty. The controller maps this to HTTP 400.
     */
    @Test
    @DisplayName("empty file throws IllegalArgumentException")
    void parse_emptyFile_throwsIllegalArgumentException() {
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, Map.of());

        var byteArray = new ByteArrayInputStream(new byte[0]);
        assertThatThrownBy(() ->
                service.parse(byteArray, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    /**
     * A header-only CSV (no data rows) must throw {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("header-only CSV (no data rows) throws IllegalArgumentException")
    void parse_headerOnly_throwsIllegalArgumentException() {
        String csv = "WellId,Signal1,Signal2";
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, Map.of());

        var stream = toStream(csv);
        assertThatThrownBy(() -> service.parse(stream, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no data rows");
    }

    /**
     * A CSV that is missing the {@code Signal2} column must throw
     * {@link IllegalArgumentException} naming the absent column.
     */
    @Test
    @DisplayName("CSV missing Signal2 column throws IllegalArgumentException naming the column")
    void parse_missingSignal2Column_throwsIllegalArgumentException() {
        String csv = "WellId,Signal1\nA1,0.45";  // Signal2 absent
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, Map.of());

        var stream = toStream(csv);
        assertThatThrownBy(() -> service.parse(stream, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SIGNAL2_COL);
    }

    /**
     * A CSV that is missing the well-identifier column must throw
     * {@link IllegalArgumentException} naming the absent column.
     */
    @Test
    @DisplayName("CSV missing well column throws IllegalArgumentException naming the column")
    void parse_missingWellColumn_throwsIllegalArgumentException() {
        String csv = "Signal1,Signal2\n0.45,0.47";  // WellId absent
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, Map.of());

        var stream = toStream(csv);
        assertThatThrownBy(() -> service.parse(stream, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(WELL_COL);
    }

    /**
     * When all rows are present but none match the mapping, the result is empty and an
     * {@link IllegalArgumentException} should be thrown.
     */
    @Test
    @DisplayName("CSV where no rows match the mapping throws IllegalArgumentException")
    void parse_noRowsMatchMapping_throwsIllegalArgumentException() {
        String csv = "WellId,Signal1,Signal2\nA1,0.1,0.2";
        // Empty mapping — no wells match
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, Map.of());
        var stream = toStream(csv);
        assertThatThrownBy(() -> service.parse(stream, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No CSV rows matched");
    }

    /**
     * A non-numeric signal value must throw {@link IllegalArgumentException} that identifies
     * the problematic cell.
     */
    @Test
    @DisplayName("non-numeric signal value throws IllegalArgumentException")
    void parse_nonNumericSignal_throwsIllegalArgumentException() {
        String csv = "WellId,Signal1,Signal2\nA1,abc,0.47";
        Map<String, WellMapping> mapping = Map.of("A1", new WellMapping(PairType.CALIBRATION, 1.0));
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, WELL_COL, SIGNAL1_COL, SIGNAL2_COL, mapping);
        var stream = toStream(csv);
        assertThatThrownBy(() -> service.parse(stream, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abc");
    }

    // -------------------------------------------------------------------------
    // Stub formats
    // -------------------------------------------------------------------------

    /**
     * TECAN format is a stub and must throw {@link UnsupportedOperationException}.
     */
    @Test
    @DisplayName("TECAN format throws UnsupportedOperationException")
    void parse_tecanFormat_throwsUnsupportedOperationException() {
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.TECAN, null, null, null, Map.of());
        var stream = toStream("");
        assertThatThrownBy(() -> service.parse(stream, config))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ByteArrayInputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
