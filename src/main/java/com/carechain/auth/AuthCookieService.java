package com.carechain.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    @Value("${carechain.auth.cookie.name:jwt}")
    private String cookieName;

    @Value("${carechain.auth.cookie.max-age-seconds:86400}")
    private long maxAgeSeconds;

    @Value("${carechain.auth.cookie.secure:false}")
    private boolean secure;

    @Value("${carechain.auth.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${carechain.auth.cookie.domain:}")
    private String cookieDomain;

    public void addAuthCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, maxAgeSeconds).toString());
    }

    public void clearAuthCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    private ResponseCookie buildCookie(String value, long maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain.trim());
        }

        return builder.build();
    }
}
