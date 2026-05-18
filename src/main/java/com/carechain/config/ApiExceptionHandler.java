package com.carechain.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiErrorException.class)
    public ResponseEntity<Map<String, Object>> handleApiError(ApiErrorException exception,
                                                              HttpServletRequest request) {
        return respond(exception.getStatus(), exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception,
                                                                HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = ApiErrorResponses.body(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI());
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                                  HttpServletRequest request) {
        String message = "Invalid value for " + exception.getName();
        if (LocalDate.class.equals(exception.getRequiredType())) {
            message = exception.getName() + " must use yyyy-MM-dd format";
        }

        return respond(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpMessageNotReadableException exception,
                                                                       HttpServletRequest request) {
        String message = "Malformed request body";
        Throwable cause = exception.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormatException
                && LocalDate.class.equals(invalidFormatException.getTargetType())) {
            message = "Date fields must use yyyy-MM-dd format";
        }

        return respond(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception,
                                                                HttpServletRequest request) {
        log.error("Unhandled exception for {}", request.getRequestURI(), exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> respond(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(ApiErrorResponses.body(status, message, path));
    }
}
