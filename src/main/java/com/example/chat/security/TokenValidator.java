package com.example.chat.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

@Component
public class TokenValidator {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    /**
     * Validates both JWT and CSRF tokens from the request headers and context.
     * @param request the incoming HTTP request
     * @return the authenticated UserDetails if valid; otherwise null
     */
    public UserDetails validateTokens(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String csrfHeader = request.getHeader("X-CSRF-TOKEN");
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");

        if (authHeader != null && authHeader.startsWith("Bearer ") &&
                csrfHeader != null && csrfToken != null) {

            String jwt = authHeader.substring(7);
            String username = jwtUtil.validateToken(jwt);

            if (username != null && csrfHeader.equals(csrfToken.getToken())) {
                return userDetailsService.loadUserByUsername(username);
            }
        }

        return null; // Invalid JWT or CSRF token
    }
}
