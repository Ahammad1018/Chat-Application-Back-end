package com.example.chat.controller;

import com.example.chat.entity.User;
import com.example.chat.entity.UserConnection;
import com.example.chat.entity.UserConversation;
import com.example.chat.service.UserConnectionService;
import com.example.chat.service.UserConversationService;
import com.example.chat.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.*;

@Controller
public class WebSocketsController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    UserConnectionService userConnectionService;

    @Autowired
    UserConversationService userConversationService;

    @Autowired
    UserService userService;

    @MessageMapping("/save-conversations")
    public ResponseEntity<?> saveConversations(@RequestBody List<UserConversation> conversations) {

        Map<String, List<StatusResponse>> responseMap = processUserConversations(conversations);

        List<StatusResponse> senderResponses = responseMap.get("Send");
        List<StatusResponse> receiverResponses = responseMap.get("Receive");

        if (receiverResponses != null){
            notifyUser(conversations.get(0).getReceiver(), receiverResponses);
        }
        notifyUser(conversations.get(0).getSender(), senderResponses);

        if (senderResponses == null || senderResponses.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        boolean hasFailure = senderResponses.stream().anyMatch(res -> res.statusCode == 400);

        return new ResponseEntity<>(hasFailure ? HttpStatus.PARTIAL_CONTENT : HttpStatus.CREATED);

    }

    public Map<String, List<StatusResponse>> processUserConversations(List<UserConversation> userConversations) {

        Map<String, List<StatusResponse>> userStatusResponses = new HashMap<>();

        for (UserConversation userConversation : userConversations) {

            String sender = userConversation.getSender();
            String receiver = userConversation.getReceiver();

            try {

                String participants = UserConnectionService.getSortedUserKey(sender, receiver);

                UserConnection connection = userConnectionService.getConnection(participants);

                int statusCode = HttpStatus.OK.value();

                boolean isBlocked = false;
                boolean isChatOpened = false;

                if (connection == null) {

                    LocalDateTime now = LocalDateTime.now();

                    connection = new UserConnection()
                            .setUserId1(userConversation.getSenderId())
                            .setUserId2(userConversation.getReceiverId())
                            .setUserName1(userConversation.getSender())
                            .setUserName2(userConversation.getReceiver())
                            .setParticipants(participants)
                            .setUser1LastConversation(userConversation.getMessage())
                            .setUser1LastConversationAt(now)
                            .setUser2LastConversation(userConversation.getMessage())
                            .setUser2LastConversationAt(now)
                            .setConnectedAt(now)
                            .setConversationsId(new HashSet<>());

                    statusCode = HttpStatus.CREATED.value();

                } else {
                    if (connection.getUserName1().equals(sender)) {
                        isBlocked = connection.isBlockedByUser2();
                        isChatOpened = connection.isUser2ChatOpened();
                    } else {
                        isBlocked = connection.isBlockedByUser1();
                        isChatOpened = connection.isUser1ChatOpened();
                    }

                    connection
                            .setUser1LastConversation(userConversation.getMessage())
                            .setUser1LastConversationAt(LocalDateTime.now());
                    if (!isBlocked) {
                        connection
                                .setUser2LastConversation(userConversation.getMessage())
                                .setUser2LastConversationAt(LocalDateTime.now());
                    }
                }

                if (isBlocked) {
                    userConversation.setStatus("Sent");
                    userConversation.setMessageDeletedByUser2(true);
                } else if (userService.isUserOnline(userConversation.getReceiver())) {
                    userConversation.setStatus(isChatOpened ? "Read" : "Delivered");
                } else {
                    userConversation.setStatus("Sent");
                }

                UserConversation conversation = userConversationService.saveConversation(userConversation);

                connection.getConversationsId().add(conversation.getId());
                connection.setUser1LastConversationId(conversation.getId())
                        .setUser1LastConversationType(conversation.getMessageType());

                if (!isBlocked) {
                    connection.setUser2LastConversationId(conversation.getId())
                            .setUser2LastConversationType(conversation.getMessageType());
                }

                UserConnection savedConnection = userConnectionService.saveConnection(connection);

                User user = userService.getUserByUserName(receiver);
                savedConnection
                        .setProfilePicture(user.getProfilePicture())
                        .setUserName(sender)
                        .setLastSeen(user.getLastSeen())
                        .setLoginStatus(user.getStatus())
                        .setEmail(user.getEmail());

                userStatusResponses.putIfAbsent("Send", new ArrayList<>());
                userStatusResponses.get("Send").add(new StatusResponse(receiver, conversation, savedConnection, statusCode, "Message Sent Successfully!", "Send"));

                if (!isBlocked) {
                    userStatusResponses.putIfAbsent("Receive", new ArrayList<>());
                    userStatusResponses.get("Receive").add(new StatusResponse(sender, conversation, savedConnection, statusCode, "Message Sent Successfully!", "Receive"));
                }

            } catch (Exception e) {
                System.out.println(e + " <------------ ");
                userStatusResponses.putIfAbsent("Send", new ArrayList<>());
                userStatusResponses.putIfAbsent("Receive", new ArrayList<>());

                userStatusResponses.get("Send").add(new StatusResponse(receiver, null, null, HttpStatus.BAD_REQUEST.value(), "Message Sending Failed!", "Send"));
                userStatusResponses.get("Receive").add(new StatusResponse(sender, null, null, HttpStatus.BAD_REQUEST.value(), "Message Sending Failed!", "Receive"));
            }
        }

        return userStatusResponses;
    }

    public void notifyUser(String username, List<StatusResponse> statusResponses) {

        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/messages",
                statusResponses
        );
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResponse {
        private String userName;
        private UserConversation conversation;
        private UserConnection connection;
        private int statusCode;
        private String message;
        private String responseType;
    }

}
