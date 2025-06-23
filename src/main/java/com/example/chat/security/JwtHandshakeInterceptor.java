package com.example.chat.security;

import com.example.chat.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    UserService userService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            String token = httpServletRequest.getParameter("key");  // e.g., ws://.../ws-chat?token=abc.def.ghi
            String sender = httpServletRequest.getParameter("sender");  // e.g., ws://.../ws-chat?sender=userName&token=abc.def.ghi

            if (token != null && sender != null) {
                String storedToken = userService.getUserByUserName(sender).getId();

                if (storedToken != null && encoder.matches(storedToken, token)) {
                    // Token is valid, create Authentication object
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER")); // adjust roles as needed
                    Authentication auth = new UsernamePasswordAuthenticationToken(sender, null, authorities);
                    // Set security context
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // Store in attributes for later access
                    attributes.put("sender", sender);
                    attributes.put("auth", true);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
