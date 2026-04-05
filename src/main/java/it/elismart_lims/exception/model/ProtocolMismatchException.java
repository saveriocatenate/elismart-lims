package it.elismart_lims.exception.model;

/**
 * Thrown when an experiment's reagent batches do not satisfy
 * the mandatory reagents required by its protocol.
 */
public class ProtocolMismatchException extends RuntimeException {

    public ProtocolMismatchException(String message) {
        super(message);
    }
}
