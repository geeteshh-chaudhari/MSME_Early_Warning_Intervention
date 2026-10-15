
package com.msme.exception;

public class DuplicateAssessmentException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateAssessmentException(String message) {
        super(message);
    }

    public DuplicateAssessmentException(
            Long snapshotId) {

        super(
            "An assessment already exists for snapshot ID: "
            + snapshotId
        );
    }

    public DuplicateAssessmentException(
            String message,
            Throwable cause) {

        super(message, cause);
    }
}
