package com.carechain.config;

import org.springframework.http.HttpStatus;

public class ApiErrorException extends RuntimeException {

    private final HttpStatus status;

    private ApiErrorException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiErrorException badRequest(String message) {
        return new ApiErrorException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiErrorException unauthorized(String message) {
        return new ApiErrorException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiErrorException forbidden(String message) {
        return new ApiErrorException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiErrorException notFound(String message) {
        return new ApiErrorException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiErrorException conflict(String message) {
        return new ApiErrorException(HttpStatus.CONFLICT, message);
    }
}
