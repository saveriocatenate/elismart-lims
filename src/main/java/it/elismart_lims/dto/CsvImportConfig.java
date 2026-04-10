package it.elismart_lims.dto;

import java.util.Map;

/**
 * Configuration for a CSV import operation.
 *
 * <p>Specifies the file format and — for {@link CsvFormat#GENERIC} — the column names that
 * identify the well, signal 1, and signal 2 values in the file. The {@code wellMapping}
 * describes which wells to import and how to classify them.</p>
 *
 * <p>Unmapped wells (wells present in the CSV but absent from {@code wellMapping}) are
 * silently skipped. Wells present in the mapping but absent from the CSV are also silently
 * skipped.</p>
 *
 * @param format        the file format (GENERIC, TECAN, BIOTEK, or SOFTMAX)
 * @param wellColumn    column name containing the well identifier (e.g. {@code "Well"});
 *                      required for {@link CsvFormat#GENERIC}, ignored for instrument-specific formats
 * @param signal1Column column name for the first replicate signal (e.g. {@code "Signal1"});
 *                      required for {@link CsvFormat#GENERIC}
 * @param signal2Column column name for the second replicate signal (e.g. {@code "Signal2"});
 *                      required for {@link CsvFormat#GENERIC}
 * @param wellMapping   maps each well identifier to its {@link WellMapping}
 *                      (pair type and optional nominal concentration)
 */
public record CsvImportConfig(
        CsvFormat format,
        String wellColumn,
        String signal1Column,
        String signal2Column,
        Map<String, WellMapping> wellMapping
) {
}
