package com.nbsb.epaysdk.core.exception;

/**
 * HTTP transport or unexpected gateway response.
 */
public class EPayHttpException extends EPayException {

    public EPayHttpException(String message) {
        super(message);
    }

    public EPayHttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
