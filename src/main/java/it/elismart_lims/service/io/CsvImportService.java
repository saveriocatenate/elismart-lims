package it.elismart_lims.service.io;

import it.elismart_lims.dto.CsvImportConfig;
import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.dto.WellMapping;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parses plate-reader CSV files into {@link MeasurementPairRequest} DTOs ready for
 * server-side entity creation.
 *
 * <h2>Supported formats</h2>
 * <ul>
 *   <li><b>GENERIC</b> — column names for well ID, signal 1, and signal 2 are read from
 *       {@link CsvImportConfig}. Each CSV row whose well ID is present in the config's
 *       {@code wellMapping} produces one {@link MeasurementPairRequest}; unmapped rows are
 *       skipped.</li>
 *   <li><b>TECAN / BIOTEK / SOFTMAX</b> — instrument-specific parsers; not yet implemented
 *       (stubs that throw {@link UnsupportedOperationException}).</li>
 * </ul>
 *
 * <h2>Derived fields</h2>
 * {@code signalMean} and {@code cvPct} are intentionally omitted from the returned DTOs —
 * they are always recalculated server-side by {@link it.elismart_lims.mapper.MeasurementPairMapper}.
 */
@Slf4j
@Service
public class CsvImportService {

    /**
     * Parses the supplied CSV stream according to the provided configuration.
     *
     * @param csv    the raw CSV content; must not be {@code null}; caller is responsible for closing
     * @param config import configuration specifying format, column names, and well layout
     * @return list of {@link MeasurementPairRequest}s parsed from the file; never {@code null}
     * @throws IOException              if the stream cannot be read
     * @throws IllegalArgumentException if the file is empty, a required column is missing,
     *                                  or no rows match the well mapping
     * @throws UnsupportedOperationException if the requested format is not yet implemented
     */
    public List<MeasurementPairRequest> parse(InputStream csv, CsvImportConfig config)
            throws IOException {
        return switch (config.format()) {
            case GENERIC  -> parseGeneric(csv, config);
            case TECAN    -> throw new UnsupportedOperationException(
                    "TECAN Magellan format is not yet implemented.");
            case BIOTEK   -> throw new UnsupportedOperationException(
                    "BioTek Gen5 format is not yet implemented.");
            case SOFTMAX  -> throw new UnsupportedOperationException(
                    "Molecular Devices SoftMax Pro format is not yet implemented.");
        };
    }

    // -------------------------------------------------------------------------
    // GENERIC parser
    // -------------------------------------------------------------------------

    /**
     * Parses a generic CSV file using the column names specified in {@code config}.
     *
     * <p>The first row is treated as the header. Every subsequent row whose well-column
     * value matches an entry in {@code config.wellMapping()} contributes one
     * {@link MeasurementPairRequest}. Rows with unrecognised well IDs are skipped with a
     * DEBUG log entry.</p>
     *
     * @param csv    input stream of the CSV file
     * @param config import configuration; {@code wellColumn}, {@code signal1Column}, and
     *               {@code signal2Column} must all be non-{@code null}
     * @return ordered list of parsed pair requests; at least one element
     * @throws IOException              if the stream cannot be read
     * @throws IllegalArgumentException if the file is empty, a required column is absent,
     *                                  a signal value cannot be parsed as a number, or no
     *                                  rows match the well mapping
     */
    private List<MeasurementPairRequest> parseGeneric(InputStream csv, CsvImportConfig config)
            throws IOException {

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = csvFormat.parse(
                new InputStreamReader(csv, StandardCharsets.UTF_8))) {

            // ── 1. Validate that the file has a non-empty header ─────────────
            Set<String> headers = parser.getHeaderMap().keySet();
            if (headers.isEmpty()) {
                throw new IllegalArgumentException(
                        "CSV file is empty or has no header row.");
            }

            // ── 2. Validate that all required columns are present ─────────────
            for (String required : List.of(
                    config.wellColumn(),
                    config.signal1Column(),
                    config.signal2Column())) {
                if (!headers.contains(required)) {
                    throw new IllegalArgumentException(
                            "Required column '" + required
                            + "' not found in CSV header. Available columns: " + headers);
                }
            }

            // ── 3. Parse data rows ────────────────────────────────────────────
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                throw new IllegalArgumentException(
                        "CSV file contains no data rows (header only).");
            }

            List<MeasurementPairRequest> result = new ArrayList<>();
            for (CSVRecord record : records) {
                String wellId = record.get(config.wellColumn());
                WellMapping mapping = config.wellMapping().get(wellId);
                if (mapping == null) {
                    log.debug("Skipping unmapped well '{}' at CSV line {}", wellId, record.getRecordNumber());
                    continue;
                }

                double s1 = parseSignal(record, config.signal1Column(), wellId);
                double s2 = parseSignal(record, config.signal2Column(), wellId);

                result.add(new MeasurementPairRequest(
                        mapping.pairType(),
                        mapping.concentrationNominal(),
                        s1,
                        s2,
                        null,   // recoveryPct — always server-side
                        false   // isOutlier
                ));
            }

            if (result.isEmpty()) {
                throw new IllegalArgumentException(
                        "No CSV rows matched the well mapping. Verify that well IDs in "
                        + "the config match those found in column '" + config.wellColumn() + "'.");
            }

            log.debug("GENERIC CSV import: {} rows matched out of {} total data rows",
                    result.size(), records.size());
            return result;
        }
    }

    /**
     * Reads a signal value from a CSV record, throwing a descriptive
     * {@link IllegalArgumentException} if the cell cannot be parsed as a double.
     *
     * @param record     the CSV row being processed
     * @param columnName the column from which to read the value
     * @param wellId     the well identifier for the error message
     * @return the parsed signal value
     */
    private double parseSignal(CSVRecord record, String columnName, String wellId) {
        String raw = record.get(columnName);
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Cannot parse signal value '" + raw + "' in column '" + columnName
                    + "' for well '" + wellId + "' (line " + record.getRecordNumber() + ").");
        }
    }
}
