package com.msme.exception;

public class ForbiddenOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenOperationException(String message) {
        super(message);
    }
}