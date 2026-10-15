
package com.msme.exception;

public class FinancialServiceUnavailableException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FinancialServiceUnavailableException(
            String message) {

        super(message);
    }

    public FinancialServiceUnavailableException(
            String message,
            Throwable cause) {

        super(message, cause);
    }
}
