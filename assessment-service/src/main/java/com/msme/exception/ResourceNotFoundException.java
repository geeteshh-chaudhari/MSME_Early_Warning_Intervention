
package com.msme.exception;

public class ResourceNotFoundException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(
            String resourceName,
            Long resourceId) {

        super(
            resourceName
            + " not found with ID: "
            + resourceId
        );
    }
}
