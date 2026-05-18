package com.carechain.config;

import com.carechain.auth.JwtUtil;
import com.carechain.auth.UserRepository;
import com.carechain.auth.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Value("${carechain.auth.cookie.name:jwt}")
    private String cookieName;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtUtil.isTokenValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String email = jwtUtil.extractEmail(token);
            userRepository.findByEmail(email).ifPresent(user -> authenticateRequest(request, email, user));
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Check Authorization header (for API calls)
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // 2. Check cookie (for Thymeleaf page navigation)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void authenticateRequest(HttpServletRequest request, String email, User user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
