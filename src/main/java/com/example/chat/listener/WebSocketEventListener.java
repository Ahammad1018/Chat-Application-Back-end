package com.example.chat.listener;

import com.example.chat.entity.User;
import com.example.chat.service.LoginService;
import com.example.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.LocalDateTime;

@Component
public class WebSocketEventListener {

    @Autowired
    private UserService userService;

    @Autowired
    private LoginService loginService;

    // Listen for new WebSocket connections
    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {

        Principal subscriber = event.getUser();
        assert subscriber != null;
        String userName = subscriber.getName();

        try {
            User user = userService.getUserByUserName(userName);
            if (user != null) {
                user.setStatus("online");
                user.setLastSeen(LocalDateTime.now());
                userService.saveUser(user);  // Save the updated user back to the database
                loginService.notifyLoginStatus(user.getUserName(), "online");
                System.out.println(userName + " connected at " + LocalDateTime.now());
            } else {
                // Handle the case where the user is not found
                System.out.println("User not found: " + userName);
            }
        } catch (Exception e) {
            // Log any errors
            System.err.println("Error handling connection for user: " + userName);
            e.printStackTrace();
        }
    }

    // Listen for WebSocket disconnections
    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {

        Principal subscriber = event.getUser();
        String userName = subscriber.getName();

        try {
            User user = userService.getUserByUserName(userName);
            if (user != null) {
                user.setStatus("offline");
                user.setLastSeen(LocalDateTime.now());
                userService.saveUser(user);  // Save the updated user back to the database
                loginService.notifyLoginStatus(user.getUserName(), "offline");
                System.out.println(userName + " disconnected at " + LocalDateTime.now());
            } else {
                // Handle the case where the user is not found
                System.out.println("User not found: " + userName);
            }
        } catch (Exception e) {
            // Log any errors
            System.err.println("Error handling disconnection for user: " + userName);
            e.printStackTrace();
        }
    }
}
