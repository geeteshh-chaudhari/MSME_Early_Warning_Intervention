package com.msme.exception;

import org.springframework.http.HttpStatus;

public class MsmeServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public MsmeServiceException(
            HttpStatus status,
            String message) {

        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static MsmeServiceException fromUpstream(int status) {

        return switch (status) {

            case 400 -> new MsmeServiceException(
                    HttpStatus.BAD_REQUEST,
                    "MS1 rejected the request; check the MSME and reporting period"
            );

            case 401 -> new MsmeServiceException(
                    HttpStatus.UNAUTHORIZED,
                    "MS1 rejected the authentication token"
            );

            case 403 -> new MsmeServiceException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to access this MSME"
            );

            case 404 -> new MsmeServiceException(
                    HttpStatus.NOT_FOUND,
                    "Requested MSME or assessment input was not found"
            );

            default -> new MsmeServiceException(
                    status >= 500 || status == 429
                            ? HttpStatus.SERVICE_UNAVAILABLE
                            : HttpStatus.BAD_GATEWAY,
                    "MS1 could not complete the request"
            );
        };
    }
}