package com.nbsb.epaysdk.core.exception;

/**
 * Signature mismatch on notify or signed responses.
 */
public class EPaySignException extends EPayException {

    public EPaySignException(String message) {
        super(message);
    }
}
