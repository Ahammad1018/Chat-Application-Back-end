package com.example.chat.service;
import com.example.chat.controller.WebSocketsController;
import com.example.chat.entity.User;
import com.example.chat.entity.UserConnection;
import com.example.chat.entity.UserConversation;
import com.example.chat.repository.LoginRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LoginService {

    @Autowired
    LoginRepository loginRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserConnectionService userConnectionService;

    @Autowired
    UserService userService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    UserConversationService userConversationService;

    public void saveData(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        loginRepository.save(user);
    }

    public void notifyLoginStatus(String userName, String status) {
        List<UserConnection> userConnections = userConnectionService.getAllConnectionsByUsername(userName);
        for (UserConnection userConnection : userConnections) {
            if (
                (userConnection.getUserName2().equals(userName) && userConnection.isUser2ChatOpened())
                ||
               (userConnection.getUserName1().equals(userName) && userConnection.isUser1ChatOpened()))
            {
                if (userConnection.getUserName2().equals(userName) && userConnection.isUser2ChatOpened()) {
                    userConnection.setUser2ChatOpened(false);
                } else {
                    userConnection.setUser1ChatOpened(false);
                }
                userConnectionService.saveConnection(userConnection);
            }

            if (status.equals("online")) {
                Set<UserConversation> userConversations = userConversationService.getConversations(userConnection.getConversationsId());

                for (UserConversation userConversation : userConversations) {
                    if (userConversation.getReceiver().equals(userName) && userConversation.getStatus().equals("Sent")) {
                        userConversation.setStatus("Delivered");
                    }
                }

                userConversationService.saveAllConversations(userConversations);
            }

            if (
                (userConnection.getUserName2().equals(userName) && userConnection.isUser1ChatOpened())
                ||
               (userConnection.getUserName1().equals(userName) && userConnection.isUser2ChatOpened()))
            {
                String receiverUserName = userConnection.getUserName2().equals(userName) ? userConnection.getUserName1() : userConnection.getUserName2();
                User user = userService.getUserByUserName(userName);
                boolean isBlocked = ((userConnection.getUserName1().equals(userName) && userConnection.isBlockedByUser1()) || (userConnection.getUserName2().equals(userName) && userConnection.isBlockedByUser2()));

                if (!isBlocked) {
                    userConnection.setEmail(user.getEmail())
                            .setLastSeen(user.getLastSeen())
                            .setLoginStatus(status)
                            .setProfilePicture(user.getProfilePicture());

                    messagingTemplate.convertAndSendToUser(
                            receiverUserName,
                            "/queue/messages",
                            List.of(new WebSocketsController.StatusResponse(userName, null, userConnection, 200, "", "Receive"))
                    );
                }
            }
        }
    }
}
