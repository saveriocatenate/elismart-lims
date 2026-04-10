package it.elismart_lims.service.curve;

/**
 * Represents a single calibration point used for curve fitting.
 *
 * <p>A calibration point pairs a known nominal concentration with the instrument
 * signal measured at that concentration. A series of these points defines the
 * calibration curve against which unknown samples are interpolated.</p>
 *
 * @param concentration nominal concentration of the calibrator (must be &gt; 0)
 * @param signal        measured instrument signal (e.g. absorbance, fluorescence)
 */
public record CalibrationPoint(double concentration, double signal) {}
