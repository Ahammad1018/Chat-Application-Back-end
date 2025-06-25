package com.example.chat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtCsrfAuthenticationFilter extends OncePerRequestFilter {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String jwtToken = getToken(request);
        String csrfHeader = request.getHeader("X-XSRF-TOKEN");

        String csrfCookie = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .map(Cookie::getValue)
                .skip(1)  // Skip the first one, to get the second one
                .findFirst()
                .orElse(null);

        // Check if there is any XSRF-TOKEN cookie (for fallback)
        boolean csrfCookieExists = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .anyMatch(c -> "XSRF-TOKEN".equals(c.getName()));

        System.out.println("JWT Token : " + jwtToken);
        System.out.println("csrfHeader : " + csrfHeader);
        System.out.println("csrfCookie : " + csrfCookie  +  " or -> " + Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .map(Cookie::getValue));
        System.out.println("csrfCookieExists : " + csrfCookieExists);

        if (jwtToken != null && csrfHeader != null && (csrfHeader.equals(csrfCookie) || csrfCookieExists)) {
            String username = jwtUtil.validateToken(jwtToken);
            if (username != null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

}
