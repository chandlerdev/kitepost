package com.kitepost.exception;

import org.springframework.http.HttpStatus;

/** An error with a message that is safe and useful to show to the user. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }

    public static ApiException bad(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, msg); }
    public static ApiException notFound(String msg) { return new ApiException(HttpStatus.NOT_FOUND, msg); }
    public static ApiException forbidden(String msg) { return new ApiException(HttpStatus.FORBIDDEN, msg); }
    public static ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, msg); }

    /** Throws a 400 with the message unless the condition holds. */
    public static void require(boolean ok, String msg) { if (!ok) throw bad(msg); }
}
