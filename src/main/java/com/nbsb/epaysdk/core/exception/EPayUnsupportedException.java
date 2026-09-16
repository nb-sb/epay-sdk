package com.nbsb.epaysdk.core.exception;

/**
 * The selected channel does not implement this API.
 */
public class EPayUnsupportedException extends EPayException {

    public EPayUnsupportedException(String message) {
        super(message);
    }
}
