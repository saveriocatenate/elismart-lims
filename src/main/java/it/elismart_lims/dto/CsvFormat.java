package it.elismart_lims.dto;

/**
 * Supported CSV file formats for plate-reader data import.
 *
 * <ul>
 *   <li>{@link #GENERIC} — configurable column mapping; supported now.</li>
 *   <li>{@link #TECAN} — Tecan Magellan export; stub (not yet implemented).</li>
 *   <li>{@link #BIOTEK} — BioTek Gen5 export; stub (not yet implemented).</li>
 *   <li>{@link #SOFTMAX} — Molecular Devices SoftMax Pro export; stub (not yet implemented).</li>
 * </ul>
 */
public enum CsvFormat {
    GENERIC,
    TECAN,
    BIOTEK,
    SOFTMAX
}
