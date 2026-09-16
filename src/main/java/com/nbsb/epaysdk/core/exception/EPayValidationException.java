package com.nbsb.epaysdk.core.exception;

/**
 * Request field failed local validation before any HTTP call.
 */
public class EPayValidationException extends EPayException {

    public EPayValidationException(String message) {
        super(message);
    }
}
