package com.nbsb.epaysdk.core.exception;

/**
 * Base unchecked error for SDK failures.
 */
public class EPayException extends RuntimeException {

    public EPayException(String message) {
        super(message);
    }

    public EPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
