package io.codzee.reviewlab.common;

/** A request conflicts with existing data, e.g. a duplicate unique value. Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
