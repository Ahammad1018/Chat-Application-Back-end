package com.example.chat.controller;

import com.example.chat.service.*;
import com.example.chat.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/conversation")
public class UserConversationController {

    @Autowired
    UserConversationService userConversationService;

    @Autowired
    UserService userService;

    @Autowired
    UserConnectionService userConnectionService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    CloudinaryService cloudinaryService;

    @Autowired
    LoginService loginService;

    private final WebSocketsController webSocketsController;

    public UserConversationController(WebSocketsController webSocketsController) {
        this.webSocketsController = webSocketsController;
    }

    @PostMapping("/upload-file/cloudinary")
    public ResponseEntity<?> uploadFileToCloudinary(@RequestParam("file") MultipartFile multipartFile) throws IOException {
        try {
            String fileURL = cloudinaryService.uploadFile(multipartFile);
            return new ResponseEntity<>(fileURL, HttpStatus.CREATED);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/upload-many-files/cloudinary")
    public ResponseEntity<?> uploadManyFilesToCloudinary(@RequestParam("files") MultipartFile[] multipartFiles) throws IOException {
        try {
            List<String> urls = new ArrayList<>(multipartFiles.length);
            for (MultipartFile file : multipartFiles){
                String fileURL = cloudinaryService.uploadFile(file);
                urls.add(fileURL);
            }
            return new ResponseEntity<>(urls, HttpStatus.CREATED);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/remove-file/cloudinary/{url}")
    public ResponseEntity<?> removeFileToCloudinary(@PathVariable String url) throws Exception {
        boolean isDeleted = cloudinaryService.deleteFile(url, "image");
        return new ResponseEntity<>(isDeleted ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }


    @GetMapping("/get-conversations/{userName}")
    public ResponseEntity<?> getConversations(Authentication authentication, @PathVariable String userName) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);

            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection == null){
                return new ResponseEntity<>("No connection data found!", HttpStatus.NO_CONTENT);
            }

            Set<UserConversation> conversations = userConversationService.getConversations(userConnection.getConversationsId());

            LocalDateTime lastConversationCleared = userConnection.getUserName1().equals(userName) ? userConnection.getRecentChatClearedByUser2() : userConnection.getRecentChatClearedByUser1();

            conversations = conversations.stream()
                    .filter(conversation -> !(
                                (conversation.getSender().equals(authUser.getUsername()) && conversation.isMessageDeletedByUser1())
                                ||
                                (conversation.getReceiver().equals(authUser.getUsername()) && conversation.isMessageDeletedByUser2())
                            ) && (
                                lastConversationCleared == null
                                ||
                                conversation.getCreatedAt().isAfter(lastConversationCleared)
                            )
                    )
                    .sorted(Comparator.comparing(UserConversation::getCreatedAt)
                            .thenComparing(UserConversation::getId))
                    .collect(Collectors.toCollection(LinkedHashSet::new)); // maintains order



            if (conversations.isEmpty()){
                return new ResponseEntity<>("No conversation data found!", HttpStatus.NO_CONTENT);
            }

            return new ResponseEntity<>(conversations, HttpStatus.OK);

        } catch (Exception e){
            return new ResponseEntity<>("An error occurred: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/change-conversation-status/{userName}")
    public ResponseEntity<?> changeConversationStatus(Authentication authentication, @PathVariable String userName){
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);

            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection == null) {
                return new ResponseEntity<>("No connection data found!", HttpStatus.NO_CONTENT);
            }

            String id = userConnection.getUser1LastConversationAt().isAfter(userConnection.getUser2LastConversationAt())
                    ? userConnection.getUser1LastConversationId()
                    : userConnection.getUser2LastConversationId();

            Optional<UserConversation> userConversation = userConversationService.getConversation(id);

            if (userConversation.isPresent() && !userConversation.get().getStatus().equals("Read")) {
                Set<UserConversation> conversations = userConversationService.getConversations(userConnection.getConversationsId());
                for (UserConversation conversation : conversations) {
                    conversation.setStatus("Read");
                }
                userConversationService.saveAllConversations(conversations);
            }

            return new ResponseEntity<>("Status changed successfully!", HttpStatus.CREATED);

        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/clear-user-conversations/{userName}")
    public ResponseEntity<?> clearUserConversations(Authentication authentication, @PathVariable String userName) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);

            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection.getUserName1().equals(authUser.getUsername())){
                userConnection.setRecentChatClearedByUser1(LocalDateTime.now());
            } else {
                userConnection.setRecentChatClearedByUser2(LocalDateTime.now());
            }

            userConnectionService.saveConnection(userConnection);

            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/delete/message/{everyone}/{userName}/{id}")
    public ResponseEntity<?> deleteMessage(Authentication authentication, @PathVariable boolean everyone, @PathVariable String userName, @PathVariable String id) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            User receiver = userService.getUserByUserName(userName);
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);
            UserConnection userConnection = userConnectionService.getConnection(participants);
            UserConversation userConversation = userConversationService.getConversation(id).get();

            boolean isDeletedByOneUser = userConversation.getSender().equals(authUser.getUsername())
                    ? userConversation.isMessageDeletedByUser2()
                    : userConversation.isMessageDeletedByUser1();


            UserConversation lastConversation = userConversationService.getLastConversation(userConnection.getConversationsId(), authUser.getUsername());
            String lastConversationId = lastConversation != null ? lastConversation.getId() : null;

            if (lastConversationId != null && lastConversationId.equals(id)) {
                UserConversation conversation = userConversationService.getLastConversationExcludeId(userConnection.getConversationsId(), new HashSet<>(Collections.singleton(id)), authUser.getUsername());
                boolean isConversationNull = conversation == null;

                String message = isConversationNull ? null : conversation.getMessage();
                String conversationId = isConversationNull ? null : conversation.getId();
                LocalDateTime createdAt = isConversationNull ? null : conversation.getCreatedAt();
                String messageType = isConversationNull ? null : conversation.getMessageType();

                if (lastConversation.getSender().equals(authUser.getUsername()) || everyone) {
                    userConnection.setUser1LastConversation(message)
                            .setUser1LastConversationId(conversationId)
                            .setUser1LastConversationAt(createdAt)
                            .setUser1LastConversationType(messageType);
                }
                if (lastConversation.getReceiver().equals(authUser.getUsername()) || everyone) {
                    userConnection.setUser2LastConversation(message)
                            .setUser2LastConversationId(conversationId)
                            .setUser2LastConversationAt(createdAt)
                            .setUser2LastConversationType(messageType);
                }

                if (!everyone && !isDeletedByOneUser){
                    userConnectionService.saveConnection(userConnection);
                }
            }

            if (!everyone && !isDeletedByOneUser) {
                if (authUser.getUsername().equals(userConversation.getSender())){
                    userConversation.setMessageDeletedByUser1(true);
                } else {
                    userConversation.setMessageDeletedByUser2(true);
                }
                userConversationService.saveConversation(userConversation);
            } else {
                userConnection.getConversationsId().remove(id);
                userConversationService.deleteConversation(id);
                userConnectionService.saveConnection(userConnection);
            }

            userConnection.setEmail(receiver.getEmail())
                    .setLastSeen(receiver.getLastSeen())
                    .setLoginStatus(receiver.getStatus())
                    .setProfilePicture(receiver.getProfilePicture());

            webSocketsController.notifyUser(authUser.getUsername(), List.of(new WebSocketsController.StatusResponse(userName, null, userConnection, 200, "", "Send")));

            if ((userName.equals(userConversation.getSender()) && userConnection.isUser1ChatOpened()) || (userName.equals(userConversation.getReceiver()) && userConnection.isUser2ChatOpened())) {
                webSocketsController.notifyUser(userName, List.of(new WebSocketsController.StatusResponse(authUser.getUsername(), null, userConnection, 200, "", "Receive")));
            }

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/delete/messages/{everyone}/{userName}")
    public ResponseEntity<?> deleteManyMessage(Authentication authentication, @PathVariable boolean everyone, @PathVariable String userName, @RequestBody List<String> ids) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);
            UserConnection userConnection = userConnectionService.getConnection(participants);
            UserConversation lastConversation = userConversationService.getLastConversation(userConnection.getConversationsId(), authUser.getUsername());

            if (lastConversation != null && ids.contains(lastConversation.getId())) {
                UserConversation lastConversationExcludeId = userConversationService.getLastConversationExcludeId(userConnection.getConversationsId(), new HashSet<>(ids), authUser.getUsername());
                boolean isConversationNull = lastConversationExcludeId == null;

                String message = isConversationNull ? null : lastConversationExcludeId.getMessage();
                String conversationId = isConversationNull ? null : lastConversationExcludeId.getId();
                LocalDateTime createdAt = isConversationNull ? null : lastConversationExcludeId.getCreatedAt();
                String messageType = isConversationNull ? null : lastConversationExcludeId.getMessageType();

                if (lastConversation.getSender().equals(authUser.getUsername()) || everyone) {
                    userConnection.setUser1LastConversation(message)
                            .setUser1LastConversationId(conversationId)
                            .setUser1LastConversationAt(createdAt)
                            .setUser1LastConversationType(messageType);
                }
                if (lastConversation.getReceiver().equals(authUser.getUsername()) || everyone) {
                    userConnection.setUser2LastConversation(message)
                            .setUser2LastConversationId(conversationId)
                            .setUser2LastConversationAt(createdAt)
                            .setUser2LastConversationType(messageType);
                }
            };

            if (!everyone) {
                List<String> deleteMany = new ArrayList<>();
                Set<String> conversationIds = userConnection.getConversationsId();
                Set<UserConversation> userConversations = new HashSet<>();

                for (String id : ids) {
                    UserConversation userConversation = userConversationService.getConversation(id).get();
                    boolean isDeletedByOneUser = userConversation.getSender().equals(authUser.getUsername()) ?
                            userConversation.isMessageDeletedByUser2() :
                            userConversation.isMessageDeletedByUser1();
                    if (isDeletedByOneUser){
                        deleteMany.add(id);
                        conversationIds.remove(id);
                    } else {
                        if (authUser.getUsername().equals(userConversation.getSender())){
                            userConversation.setMessageDeletedByUser1(true);
                        } else {
                            userConversation.setMessageDeletedByUser2(true);
                        }
                        userConversations.add(userConversation);
                    }
                }

                if (!deleteMany.isEmpty()) {
                    userConversationService.deleteManyConversation(deleteMany);
                }
                userConversationService.saveAllConversations(userConversations);
                userConnection.setConversationsId(conversationIds);
                userConnectionService.saveConnection(userConnection);
            } else {
                Set<String> conversationsIds = userConnection.getConversationsId();
                for (String id : ids) {
                    conversationsIds.remove(id);
                }
                userConnection.setConversationsId(conversationsIds);
                userConversationService.deleteManyConversation(ids);
                userConnectionService.saveConnection(userConnection);
            }

            webSocketsController.notifyUser(authUser.getUsername(), List.of(new WebSocketsController.StatusResponse(userName, null, userConnection, 200, "", "Send")));
            if ((userConnection.getUserName1().equals(userName) && userConnection.isUser1ChatOpened()) || (userConnection.getUserName2().equals(userName) && userConnection.isUser2ChatOpened())) {
                webSocketsController.notifyUser(userName, List.of(new WebSocketsController.StatusResponse(authUser.getUsername(), null, userConnection, 200, "", "Receive")));
            }

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/clear-chat/{userName}")
    public ResponseEntity<?> manageClearChat(Authentication authentication, @PathVariable String userName) {

        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            User user = userService.getUserByUserName(authUser.getUsername());

            String participants = UserConnectionService.getSortedUserKey(user.getUserName(), userName);
            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection.getUserName1().equals(user.getUserName())){
                userConnection.setRecentChatClearedByUser1(LocalDateTime.now())
                        .setUser2LastConversationId(null)
                        .setUser2LastConversation(null)
                        .setUser2LastConversationType(null)
                        .setUser2LastConversationAt(null);
            } else if (userConnection.getUserName2().equals(user.getUserName())) {
                userConnection.setRecentChatClearedByUser2(LocalDateTime.now())
                        .setUser1LastConversationId(null)
                        .setUser1LastConversation(null)
                        .setUser1LastConversationType(null)
                        .setUser1LastConversationAt(null);
            }

            userConnectionService.saveConnection(userConnection);

            User receiver = userService.getUserByUserName(userName);
            userConnection.setEmail(receiver.getEmail())
                            .setProfilePicture(receiver.getProfilePicture())
                            .setLoginStatus(receiver.getStatus())
                            .setLastSeen(receiver.getLastSeen());

            messagingTemplate.convertAndSendToUser(
                    user.getUserName(),
                    "/queue/messages",
                    List.of(new WebSocketsController.StatusResponse(userName, null, userConnection, 200, "", "Send"))
            );

            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }

}
