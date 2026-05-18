package com.carechain.config;

import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiErrorResponses {

    private ApiErrorResponses() {
    }

    public static Map<String, Object> body(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        body.put("path", path);
        body.put("timestamp", OffsetDateTime.now().toString());
        return body;
    }
}
