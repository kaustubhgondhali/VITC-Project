package com.vitc.exception;

/** Authenticated but not allowed to touch this resource -> HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
